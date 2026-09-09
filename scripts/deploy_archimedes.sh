#!/usr/bin/env bash
set -Eeuo pipefail

# Build and publish the canonical, persistently signed SMS Forwarder release.
# This script is deliberately host-specific: the Android SDK and release
# keystore remain on Archimedes and are never copied into the repository.

REPO_ROOT="${SMS_FORWARDER_REPO:-/home/ubuntu/Projects/sms_forwarder}"
BRANCH="${SMS_FORWARDER_BRANCH:-master}"
PUBLIC_DIR="${SMS_FORWARDER_PUBLIC_DIR:-/var/www/html/apks}"
HANDOFF_DIR="${SMS_FORWARDER_HANDOFF_DIR:-/home/ubuntu/phone_share/apks/sms_forwarder}"
STATE_DIR="${SMS_FORWARDER_STATE_DIR:-${XDG_STATE_HOME:-$HOME/.local/state}/sms-forwarder}"
EXPECTED_SIGNER_SHA256="${SMS_FORWARDER_SIGNER_SHA256:-773f6a803c33c64aa36c7fa0a9f7c0a8dcd9b262edaf9b237e8c1087b0431715}"

log() {
    printf '[sms-forwarder-deploy] %s\n' "$*"
}

fail() {
    log "ERROR: $*"
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "required command is missing: $1"
}

[[ "$(hostname -s)" == "archimedes" ]] || fail "this deploy script only runs on archimedes"
[[ -d "$REPO_ROOT/.git" ]] || fail "repository is missing: $REPO_ROOT"
require_command git
require_command flock
require_command install
require_command sha256sum

mkdir -p "$STATE_DIR"
chmod 700 "$STATE_DIR"
exec 9>"$STATE_DIR/deploy.lock"
if ! flock -n 9; then
    log 'another deployment is already running; leaving it to finish'
    exit 0
fi

failure_file="$STATE_DIR/last-failure.txt"
deployed_sha_file="$STATE_DIR/deployed.sha"
trap 'rc=$?; if (( rc != 0 )); then printf "exit=%s\ntime=%s\nrepo=%s\ncommit=%s\n" "$rc" "$(date -Is)" "$REPO_ROOT" "${head_sha:-unknown}" > "$failure_file" || true; fi' EXIT

current_branch="$(git -C "$REPO_ROOT" symbolic-ref --short HEAD 2>/dev/null || true)"
[[ "$current_branch" == "$BRANCH" ]] || fail "expected branch $BRANCH, found ${current_branch:-detached}"

changes="$(git -C "$REPO_ROOT" status --porcelain --untracked-files=all)"
[[ -z "$changes" ]] || fail "refusing to deploy a dirty checkout; inspect git status"

log "fetching origin/$BRANCH"
git -C "$REPO_ROOT" fetch --prune origin "$BRANCH"

read -r ahead behind <<<"$(git -C "$REPO_ROOT" rev-list --left-right --count "HEAD...origin/$BRANCH")"
(( ahead == 0 )) || fail "checkout has $ahead local commit(s) not on origin/$BRANCH"
if (( behind > 0 )); then
    log "fast-forwarding checkout by $behind commit(s)"
    git -C "$REPO_ROOT" merge --ff-only "origin/$BRANCH"
fi

head_sha="$(git -C "$REPO_ROOT" rev-parse HEAD)"
changes="$(git -C "$REPO_ROOT" status --porcelain --untracked-files=all)"
[[ -z "$changes" ]] || fail "checkout became dirty during synchronization"

version_code="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]+([0-9]+).*$/\1/p' "$REPO_ROOT/app/build.gradle" | head -n 1)"
version_name="$(sed -nE 's/^[[:space:]]*versionName[[:space:]]+"([^"]+)".*$/\1/p' "$REPO_ROOT/app/build.gradle" | head -n 1)"
[[ "$version_code" =~ ^[0-9]+$ ]] || fail 'could not read a numeric versionCode from app/build.gradle'
[[ "$version_name" =~ ^[0-9A-Za-z._-]+$ ]] || fail 'could not read a safe versionName from app/build.gradle'

sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/.android-sdk}}"
if [[ ! -d "$sdk_dir/build-tools" && -f "$REPO_ROOT/local.properties" ]]; then
    configured_sdk="$(sed -nE 's/^sdk\.dir=(.*)$/\1/p' "$REPO_ROOT/local.properties" | head -n 1)"
    [[ -n "$configured_sdk" ]] && sdk_dir="$configured_sdk"
fi

aapt="$(find "$sdk_dir/build-tools" -maxdepth 2 -type f -name aapt -print 2>/dev/null | sort -V | tail -n 1 || true)"
apksigner="$(find "$sdk_dir/build-tools" -maxdepth 2 -type f -name apksigner -print 2>/dev/null | sort -V | tail -n 1 || true)"
[[ -x "$aapt" ]] || fail "Android aapt was not found below $sdk_dir/build-tools"
[[ -x "$apksigner" ]] || fail "Android apksigner was not found below $sdk_dir/build-tools"

artifact_name="sms-code-forwarder-${version_name}.apk"
artifact="$REPO_ROOT/app/build/outputs/apk/release/app-release.apk"

if [[ -f "$deployed_sha_file" ]] && [[ "$(tr -d '[:space:]' < "$deployed_sha_file")" == "$head_sha" ]] \
        && [[ -f "$PUBLIC_DIR/sms-code-forwarder-latest.apk" ]] \
        && [[ -f "$HANDOFF_DIR/$artifact_name" ]]; then
    log "commit $head_sha is already deployed"
    exit 0
fi

deployed_version_code=""
if [[ -f "$PUBLIC_DIR/sms-code-forwarder-latest.apk" ]]; then
    deployed_version_code="$("$aapt" dump badging "$PUBLIC_DIR/sms-code-forwarder-latest.apk" 2>/dev/null | sed -nE "s/.*versionCode='([0-9]+)'.*/\1/p" | head -n 1 || true)"
fi
if [[ "$deployed_version_code" =~ ^[0-9]+$ ]] && (( version_code <= deployed_version_code )); then
    fail "versionCode $version_code is not newer than public versionCode $deployed_version_code; bump the app version before deploying"
fi

gradle_bin="${SMS_FORWARDER_GRADLE:-}"
if [[ -n "$gradle_bin" ]]; then
    [[ -x "$gradle_bin" ]] || fail "SMS_FORWARDER_GRADLE is not executable: $gradle_bin"
elif [[ -x "$REPO_ROOT/gradlew" ]]; then
    gradle_bin="$REPO_ROOT/gradlew"
else
    system_gradle="$(command -v gradle || true)"
    if [[ -n "$system_gradle" ]]; then
        system_major="$("$system_gradle" --version 2>/dev/null | sed -nE 's/^Gradle ([0-9]+).*/\1/p' | head -n 1 || true)"
        [[ "$system_major" =~ ^[0-9]+$ ]] && (( system_major >= 8 )) && gradle_bin="$system_gradle"
    fi
    if [[ -z "$gradle_bin" ]]; then
        gradle_bin="$(find "$HOME/.gradle/wrapper/dists" -maxdepth 6 -type f -path '*/bin/gradle' -print 2>/dev/null | sort -V | tail -n 1 || true)"
    fi
    [[ -x "$gradle_bin" ]] || fail 'no compatible Gradle 8+ executable was found; set SMS_FORWARDER_GRADLE'
fi

log "building $version_name (versionCode $version_code) from $head_sha"
rm -f "$artifact"
"$gradle_bin" --no-daemon --console=plain -p "$REPO_ROOT" testDebugUnitTest assembleRelease
[[ -f "$artifact" ]] || fail "Gradle completed without producing $artifact"

changes="$(git -C "$REPO_ROOT" status --porcelain --untracked-files=all)"
[[ -z "$changes" ]] || fail "build changed tracked files in the source checkout"

badging="$("$aapt" dump badging "$artifact")"
grep -Fq "package: name='com.tahlor.smsforwarder'" <<<"$badging" \
    || fail 'release APK has the wrong application ID'
grep -Fq "versionCode='$version_code' versionName='$version_name'" <<<"$badging" \
    || fail 'release APK metadata does not match app/build.gradle'

cert_output="$("$apksigner" verify --print-certs "$artifact" 2>&1)" \
    || fail "apksigner rejected the release APK: $cert_output"
grep -Fq "Signer #1 certificate SHA-256 digest: $EXPECTED_SIGNER_SHA256" <<<"$cert_output" \
    || fail 'release APK is not signed by the configured persistent certificate'

sha256="$(sha256sum "$artifact" | awk '{print $1}')"
mkdir -p "$HANDOFF_DIR"
handoff_tmp="$HANDOFF_DIR/.${artifact_name}.tmp.$$"
install -m 0644 "$artifact" "$handoff_tmp"
mv -f "$handoff_tmp" "$HANDOFF_DIR/$artifact_name"

checksums_tmp="$HANDOFF_DIR/.APK_CHECKSUMS.txt.tmp.$$"
{
    if [[ -f "$HANDOFF_DIR/APK_CHECKSUMS.txt" ]]; then
        grep -v -F "  $artifact_name" "$HANDOFF_DIR/APK_CHECKSUMS.txt" || true
    fi
    printf '%s  %s\n' "$sha256" "$artifact_name"
} >"$checksums_tmp"
mv -f "$checksums_tmp" "$HANDOFF_DIR/APK_CHECKSUMS.txt"

release_tmp="$HANDOFF_DIR/.RELEASE.md.tmp.$$"
{
    printf '# SMS Code Forwarder %s\n\n' "$version_name"
    printf -- '- Commit: `%s`\n' "$head_sha"
    printf -- '- Version code: `%s`\n' "$version_code"
    printf -- '- Build: `testDebugUnitTest assembleRelease`\n'
    printf -- '- APK: `%s`\n' "$artifact_name"
    printf -- '- SHA-256: `%s`\n' "$sha256"
    printf '\nThe APK is persistently signed for update-in-place installation.\n'
} >"$release_tmp"
mv -f "$release_tmp" "$HANDOFF_DIR/RELEASE.md"

sudo -n install -d -m 0755 "$PUBLIC_DIR"
public_tmp="$PUBLIC_DIR/.${artifact_name}.tmp.$$"
latest_tmp="$PUBLIC_DIR/.sms-code-forwarder-latest.apk.tmp.$$"
sudo -n install -m 0644 "$artifact" "$public_tmp"
sudo -n install -m 0644 "$artifact" "$latest_tmp"
sudo -n mv -f "$public_tmp" "$PUBLIC_DIR/$artifact_name"
sudo -n mv -f "$latest_tmp" "$PUBLIC_DIR/sms-code-forwarder-latest.apk"

state_tmp="$STATE_DIR/.deployed.sha.tmp.$$"
printf '%s\n' "$head_sha" >"$state_tmp"
mv -f "$state_tmp" "$deployed_sha_file"
rm -f "$failure_file"

log "published $artifact_name (sha256 $sha256) from $head_sha"

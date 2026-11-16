#!/usr/bin/env bash
set -Eeuo pipefail

# Archimedes canonical publisher for the independent SMS Link Automator app.
# Publishes only verified, persistently signed RELEASE APKs into the SSO APK
# Library's existing phone-share root; never promotes CI debug artifacts.
REPO_ROOT="${SMS_FORWARDER_REPO:-/home/ubuntu/Projects/sms_forwarder}"
BRANCH="${SMS_FORWARDER_BRANCH:-master}"
HANDOFF_ROOT="${SMS_LINK_HANDOFF_ROOT:-/home/ubuntu/phone_share/apks}"
STATE_DIR="${SMS_LINK_STATE_DIR:-${XDG_STATE_HOME:-$HOME/.local/state}/sms-link-automator}"
SHARED_STATE_DIR="${SMS_FORWARDER_STATE_DIR:-${XDG_STATE_HOME:-$HOME/.local/state}/sms-forwarder}"
EXPECTED_SIGNER_SHA256="${SMS_FORWARDER_SIGNER_SHA256:-773f6a803c33c64aa36c7fa0a9f7c0a8dcd9b262edaf9b237e8c1087b0431715}"
log() { printf '[sms-link-deploy] %s\n' "$*"; }
fail() { log "ERROR: $*"; exit 1; }
[[ "$(hostname -s)" == "archimedes" ]] || fail "host must be archimedes"
[[ -d "$REPO_ROOT/.git" ]] || fail "missing repository: $REPO_ROOT"
for command in git flock find sha256sum install; do command -v "$command" >/dev/null || fail "missing $command"; done

mkdir -p "$STATE_DIR" "$SHARED_STATE_DIR"
chmod 700 "$STATE_DIR"
# Shared lock prevents competing fast-forwards with SMS Forwarder's timer.
exec 9>"$SHARED_STATE_DIR/deploy.lock"
flock -w 120 9 || fail 'checkout deployment lock busy'
deployed_sha_file="$STATE_DIR/deployed.sha"
failure_file="$STATE_DIR/last-failure.txt"
trap 'rc=$?; if (( rc != 0 )); then printf "exit=%s\ntime=%s\ncommit=%s\n" "$rc" "$(date -Is)" "${head_sha:-unknown}" > "$failure_file" || true; fi' EXIT
[[ "$(git -C "$REPO_ROOT" symbolic-ref --short HEAD)" == "$BRANCH" ]] || fail 'checkout is on wrong branch'
[[ -z "$(git -C "$REPO_ROOT" status --porcelain --untracked-files=all)" ]] || fail 'dirty checkout; will not overwrite changes'

git -C "$REPO_ROOT" fetch --prune origin "$BRANCH"
read -r ahead behind <<<"$(git -C "$REPO_ROOT" rev-list --left-right --count "HEAD...origin/$BRANCH")"
(( ahead == 0 )) || fail "local branch is $ahead commit(s) ahead; refusing divergence"
if (( behind > 0 )); then git -C "$REPO_ROOT" merge --ff-only "origin/$BRANCH"; fi
head_sha="$(git -C "$REPO_ROOT" rev-parse HEAD)"
[[ -z "$(git -C "$REPO_ROOT" status --porcelain --untracked-files=all)" ]] || fail 'checkout dirty after sync'

gradle_source="$REPO_ROOT/linkautomator/build.gradle"
version_code="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]+([0-9]+).*/\1/p' "$gradle_source" | head -n 1)"
version_name="$(sed -nE "s/^[[:space:]]*versionName[[:space:]]+'([^']+)'.*/\1/p" "$gradle_source" | head -n 1)"
[[ "$version_code" =~ ^[0-9]+$ ]] || fail 'invalid versionCode'
[[ "$version_name" =~ ^[0-9A-Za-z._-]+$ ]] || fail 'invalid versionName'
handoff_dir="$HANDOFF_ROOT/sms_link_automator"
latest="$handoff_dir/sms-link-automator-latest.apk"
artifact_name="sms-link-automator-${version_name}.apk"
release="$REPO_ROOT/linkautomator/build/outputs/apk/release/linkautomator-release.apk"

if [[ -f "$deployed_sha_file" && -s "$latest" ]]; then
    old_sha="$(tr -d '[:space:]' < "$deployed_sha_file")"
    if [[ "$old_sha" == "$head_sha" ]]; then log "already deployed $head_sha"; exit 0; fi
    if git -C "$REPO_ROOT" cat-file -e "$old_sha^{commit}" 2>/dev/null &&
       git -C "$REPO_ROOT" diff --quiet "$old_sha" "$head_sha" -- linkautomator/; then
        log "no Link Automator changes since deployed commit $old_sha"
        exit 0
    fi
fi

sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/.android-sdk}}"
if [[ ! -d "$sdk_dir/build-tools" && -f "$REPO_ROOT/local.properties" ]]; then
    configured="$(sed -nE 's/^sdk\.dir=(.*)$/\1/p' "$REPO_ROOT/local.properties" | head -n 1)"
    [[ -n "$configured" ]] && sdk_dir="$configured"
fi
aapt="$(find "$sdk_dir/build-tools" -maxdepth 2 -type f -name aapt -print 2>/dev/null | sort -V | tail -n 1 || true)"
apksigner="$(find "$sdk_dir/build-tools" -maxdepth 2 -type f -name apksigner -print 2>/dev/null | sort -V | tail -n 1 || true)"
[[ -x "$aapt" && -x "$apksigner" ]] || fail 'Android build tools missing'

if [[ -f "$latest" ]]; then
    deployed_version_code="$("$aapt" dump badging "$latest" 2>/dev/null |
         sed -nE "s/.*versionCode='([0-9]+)'.*/\1/p" | head -n 1 || true)"
    if [[ "$deployed_version_code" =~ ^[0-9]+$ ]] && (( version_code <= deployed_version_code )); then
        fail "source versionCode $version_code must exceed published $deployed_version_code"
    fi
fi

gradle_bin="${SMS_FORWARDER_GRADLE:-}"
if [[ -n "$gradle_bin" ]]; then
    [[ -x "$gradle_bin" ]] || fail "Gradle not executable: $gradle_bin"
elif [[ -x "$REPO_ROOT/gradlew" ]]; then
    gradle_bin="$REPO_ROOT/gradlew"
else
    gradle_bin="$(command -v gradle || true)"
    if [[ -z "$gradle_bin" ]]; then
        gradle_bin="$(find "$HOME/.gradle/wrapper/dists" -maxdepth 6 -type f -path '*/bin/gradle' -print 2>/dev/null | sort -V | tail -n 1 || true)"
    fi
    [[ -x "$gradle_bin" ]] || fail 'Gradle 8+ not installed; set SMS_FORWARDER_GRADLE'
fi

log "building signed release $version_name code=$version_code commit=$head_sha"
rm -f "$release"
"$gradle_bin" --no-daemon --console=plain -p "$REPO_ROOT" \
    :linkautomator:testDebugUnitTest :linkautomator:lintDebug :linkautomator:assembleRelease
[[ -s "$release" ]] || fail "no signed release APK produced: $release"
[[ -z "$(git -C "$REPO_ROOT" status --porcelain --untracked-files=all)" ]] || fail 'build modified checkout'

badging="$("$aapt" dump badging "$release")"
grep -Fq "package: name='com.tahlor.smslinkautomator'" <<<"$badging" || fail 'wrong APK package'
grep -Fq "versionCode='$version_code' versionName='$version_name'" <<<"$badging" || fail 'wrong version'
signer_output="$("$apksigner" verify --print-certs "$release" 2>&1)" || fail 'APK signature verification failed'
grep -Fq "Signer #1 certificate SHA-256 digest: $EXPECTED_SIGNER_SHA256" <<<"$signer_output" ||
    fail 'APK signed with unexpected certificate'

sha="$(sha256sum "$release" | awk '{print $1}')"
mkdir -p "$handoff_dir"
versioned_temp="$handoff_dir/.$artifact_name.tmp.$$"
latest_temp="$handoff_dir/.sms-link-automator-latest.apk.tmp.$$"
install -m 0644 "$release" "$versioned_temp"
install -m 0644 "$release" "$latest_temp"
mv -f "$versioned_temp" "$handoff_dir/$artifact_name"
mv -f "$latest_temp" "$latest"
printf '%s  %s\n' "$sha" "$artifact_name" > "$handoff_dir/.APK_CHECKSUMS.txt.tmp.$$"
mv -f "$handoff_dir/.APK_CHECKSUMS.txt.tmp.$$" "$handoff_dir/APK_CHECKSUMS.txt"
printf '%s  %s\n' "$sha" "sms-link-automator-latest.apk" > "$handoff_dir/.latest.apk.sha256.tmp.$$"
mv -f "$handoff_dir/.latest.apk.sha256.tmp.$$" "$handoff_dir/sms-link-automator-latest.apk.sha256"
cat > "$handoff_dir/.RELEASE.md.tmp.$$" <<EOF
# SMS Link Automator $version_name

- Package: com.tahlor.smslinkautomator
- Version code: $version_code
- Source commit: $head_sha
- SHA-256: $sha
- Build: :linkautomator:testDebugUnitTest :linkautomator:lintDebug :linkautomator:assembleRelease
- Release: persistently signed and verified
- Download: https://taylorarchibald.com/apk_downloads/download/sms_link_automator/sms-link-automator-latest.apk
- Notes: SMS receipt requires Android permission; background HTTPS GET is not the same as a successful web claim.
EOF
mv -f "$handoff_dir/.RELEASE.md.tmp.$$" "$handoff_dir/RELEASE.md"
printf '%s\n' "$head_sha" > "$STATE_DIR/.deployed.sha.tmp.$$"
mv -f "$STATE_DIR/.deployed.sha.tmp.$$" "$deployed_sha_file"
rm -f "$failure_file"
log "PUBLISHED $latest commit=$head_sha sha256=$sha; SSO APK library should now list it"

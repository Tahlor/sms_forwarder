# SMS Code Forwarder

A tiny sideload-only Android app for forwarding newly received SMS messages to one or more registered phones and relaying short-code conversations.

## Registered phone profiles

The app no longer exposes separate "destination" and "relay controller" numbers. A phone number is registered once and gets its own settings:

- **Forward SMS to this number**
- **Only forward messages containing 6+ consecutive digits** (`[0-9]{6,}`)
- **Also send the extracted code as a second SMS** for easy copying
- **Allow this number to control `[711-711]` shortcode relay**

Multiple phones can be registered with different combinations. Existing single-number settings migrate automatically into profiles on upgrade. If the old relay controller was genuinely different from the old destination number, it becomes a second relay-only profile.

## SMS permission setup

The app needs only `RECEIVE_SMS` and `SEND_SMS`.

On launch it automatically requests whichever of those two permissions are missing. These are hard-restricted Android permissions, so a sideloaded APK may also require a one-time installer/user approval:

1. Open **App Info** for SMS Code Forwarder.
2. Open the top-right `⋮` menu.
3. Choose **Allow restricted settings** if Android presents that option.
4. Return to SMS Code Forwarder; it immediately retries the missing SMS permission request.

The app includes buttons for both **Grant send + receive SMS permissions** and **Open App Info / Allow restricted settings**. There is no way for a third-party app to silently enable restricted settings itself.

## OTP forwarding

By default a forwarding-enabled phone profile forwards only messages containing **6 or more consecutive digits**. If its code-copy option is enabled, the app sends the full forwarded SMS first and then a second SMS whose entire body is the first qualifying digit sequence.

Six-digit sender/shortcode labels are displayed with a dash so they do not look like a second six-digit verification code. Sender `711711`, for example, is shown as `711-711`; the raw SMS address remains unchanged internally.

## Trusted shortcode relay

Any registered phone with shortcode relay enabled can control six-digit SMS short codes through the forwarding phone.

```text
[711-711] Y
```

sends only `Y` to raw shortcode `711711` and opens a 5-minute reply window for that registered controller. Replies are sent back as:

```text
[711-711] <reply text>
```

The undashed `[711711]` command remains accepted for compatibility. Each registered controller has its own active 5-minute session, and temporary sessions are not included in backup.

## Settings persistence and deletion

Registered-phone configuration is stored separately from runtime state and participates in Android's supported backup/restore infrastructure. The manifest enables backup and explicitly includes only `sms_forwarder.xml`; transient status text and active shortcode sessions live in `sms_forwarder_runtime.xml` and are excluded.

This means Android can normally restore the user's registered phones after uninstall/reinstall or device migration when backup/restore is available. Android controls the backup transport and timing, so this cannot be made an absolute guarantee for every device/account. SMS permissions are cleared by uninstall and must be granted again.

The app has an explicit **Delete saved setup** action. It clears all registered-phone configuration, stores a deletion marker, clears runtime state, and notifies Android's backup manager that the backed-up state changed.

## Updating the app

The settings screen has an **Update app** button that opens:

```text
https://taylorarchibald.com/apks/sms-code-forwarder-latest.apk
```

in the device browser. The forwarder itself therefore keeps **no Internet permission**.

### Signing/update invariant

Android updates require all of the following:

1. the same application ID (`com.tahlor.smsforwarder`);
2. the same signing certificate as the installed canonical build;
3. a strictly higher `versionCode` for each newer release.

Canonical releases must be signed with one persistent key. `assembleRelease` and `bundleRelease` fail if release signing credentials are absent. Archimedes should publish only that persistently signed release APK at the stable update URL, always with a higher `versionCode`, so Android can update it in place.

If an older installed copy was signed by a different/unknown key, migration requires **one uninstall/reinstall**: uninstall the old copy, install the canonical persistent-key APK, and then retain that signing lineage forever. Do not migrate users onto a temporary/debug signer merely to make one installation succeed.

## Privacy / security

- Uses only `RECEIVE_SMS` and `SEND_SMS` runtime permissions.
- Does **not** request `READ_SMS` or Internet access.
- Does not scan SMS history.
- Does not store message bodies or verification codes.
- Backs up only user-configured phone profiles, not transient relay/session state.
- Prefixes full relayed OTP messages with `[SMS Forwarder]` and ignores already-prefixed messages to prevent forwarding loops.

## Build

Requires Java 17-compatible Android build tooling and Android SDK 35.

For normal CI/debug validation:

```bash
gradle testDebugUnitTest assembleDebug
```

For a canonical publishable build on Archimedes, configure the persistent signer and run:

```bash
gradle testDebugUnitTest assembleRelease
```

The publishable release APK is under `app/build/outputs/apk/release/`. Verify its package, version, SHA-256, permissions, and signing certificate fingerprint before publishing it.

GitHub Actions builds every push to `master` and uploads a debug APK as test/build evidence. It is not the canonical update artifact unless explicitly configured with the same persistent signer.

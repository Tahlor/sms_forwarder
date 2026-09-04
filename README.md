# SMS Code Forwarder

A tiny sideload-only Android app for forwarding newly received SMS messages to registered downstream phones and relaying shortcode conversations.

## Setup model

A phone number is registered once. Each registered phone independently controls:

- whether it receives forwarded SMS;
- whether forwarding is limited to messages containing 6+ consecutive digits (`[0-9]{6,}`);
- whether it also gets a second SMS containing only the extracted code;
- whether it can control six-digit SMS shortcodes.

There are no separate destination/controller number fields. Multiple downstream phones are supported by adding another registered phone.

## Sideload authorization

The app requests only `RECEIVE_SMS` and `SEND_SMS`. On Android 13+, sideloaded apps can have sensitive permissions blocked by Restricted Settings. The app uses one guided **Authorize SMS access** flow:

1. it requests the two SMS permissions;
2. if Android blocks them, it explains the one-time App Info → top-right `⋮` → **Allow restricted settings** step;
3. when the user returns from App Info, the app automatically retries the SMS permission request.

Android does not expose a public API that lets an app silently toggle **Allow restricted settings** itself. Successful `RECEIVE_SMS` + `SEND_SMS` grants are the app's readiness check. Google documents the restricted-settings flow for sideloaded apps at Android Help.

## OTP forwarding

A forwarding-enabled phone profile normally forwards only messages containing 6+ consecutive digits. If code-copy is enabled, the app sends the full forwarded message and then a second SMS containing only the first qualifying digit sequence.

Six-digit sender/shortcode labels are displayed with a dash so they do not look like a second OTP. Sender `711711`, for example, is displayed as `711-711`; the raw SMS address is unchanged internally.

## Shortcode relay

A registered phone with shortcode control enabled can send this to the phone running the app:

```text
[711711] SAVE
```

or:

```text
[711-711] SAVE
```

The app matches the inbound sender to the registered phone, sends only `SAVE` to raw shortcode `711711`, and opens a 5-minute reply window for that controller. Replies from the shortcode are returned as:

```text
[711-711] <reply text>
```

`[711711]` with no payload only opens the reply window. Each registered controller has its own temporary session.

Bracketed shortcode commands are now handled explicitly rather than silently falling through to ordinary forwarding. Runtime status reports whether the command came from an unregistered phone, shortcode control was disabled, `SEND_SMS` was missing, the send was queued, or Android/carrier rejected it synchronously.

## In-app examples / diagnostics

The **Examples & help** page shows:

- current Receive/Send SMS permission state;
- every registered phone and its enabled behaviors;
- the latest runtime status;
- copyable examples for OTP forwarding, `[711711] SAVE`, reply windows, multiple phone profiles, and restricted-settings troubleshooting.

## Settings persistence and deletion

Registered-phone configuration participates in Android backup/restore. Only durable configuration is backed up; transient status and active 5-minute relay sessions are kept separately and excluded. Uninstall clears SMS permissions, so authorization must be granted again.

**Delete saved setup** clears the registered phones and runtime state and notifies Android Backup Manager of the deletion.

## Updating the app

**Update app** opens:

```text
https://taylorarchibald.com/apks/sms-code-forwarder-latest.apk
```

in the device browser. The app itself therefore retains no Internet permission.

Canonical releases must keep application ID `com.tahlor.smsforwarder`, use the same persistent signing certificate, and increase `versionCode`. Release builds fail if the persistent signer is not configured. GitHub Actions debug APKs are build evidence, not the canonical update artifact unless deliberately signed with the same persistent key.

## Privacy / security

- `RECEIVE_SMS` + `SEND_SMS` only.
- No `READ_SMS`.
- No Internet permission.
- No SMS-history scan.
- No message bodies or verification codes persisted.
- Full forwarded OTP messages use `[SMS Forwarder]` and already-prefixed messages are ignored to prevent forwarding loops.

## Build

Requires Java 17-compatible Android build tooling and Android SDK 35.

```bash
gradle testDebugUnitTest assembleDebug
```

Canonical Archimedes release builds use the persistent signer and:

```bash
gradle testDebugUnitTest assembleRelease
```

Version: **0.1.5 / versionCode 6**.

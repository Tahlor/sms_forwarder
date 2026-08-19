# SMS Code Forwarder

A tiny sideload-only Android app for forwarding newly received SMS messages to another phone number.

By default it forwards only messages containing **6 or more consecutive digits** (`[0-9]{6,}`), which catches most verification/2FA codes without forwarding ordinary texts. The filter can be disabled in the app to forward all newly received SMS messages.

## Privacy / security

- Uses only `RECEIVE_SMS` and `SEND_SMS` runtime permissions.
- Does **not** request `READ_SMS` or Internet access.
- Does not scan SMS history.
- Does not store message bodies or verification codes.
- Stores only destination/filter/enabled settings plus a short status string.
- Prefixes relayed messages with `[SMS Forwarder]` and ignores already-prefixed messages to prevent forwarding loops.

## Build

Requires Java 17 and Android SDK 35.

```bash
gradle testDebugUnitTest assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions also builds every push to `master` and uploads the APK as the `sms-code-forwarder-apk` workflow artifact.

## Install / configure

1. Sideload the APK on the phone receiving the source SMS messages.
2. Open **SMS Code Forwarder** and grant SMS receive/send permissions.
3. Enter the destination phone number.
4. Leave **Only forward messages containing 6+ consecutive digits** checked for the default code-only mode.
5. Enable forwarding and save.

The app receives only new messages after installation. Android may show warnings because SMS permissions are sensitive; this project is intended for personal sideloading rather than Play Store distribution.

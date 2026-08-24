# SMS Code Forwarder

A tiny sideload-only Android app for forwarding newly received SMS messages to another phone number.

By default it forwards only messages containing **6 or more consecutive digits** (`[0-9]{6,}`), which catches most verification/2FA codes without forwarding ordinary texts. The filter can be disabled in the app to forward all newly received SMS messages.

By default, when a forwarded message contains such a code, the app also sends a **second SMS whose entire body is just the extracted digit sequence**. This makes the code easy to copy even when Android/Google Messages does not recognize the forwarded message as an OTP. The extra code-only SMS can be disabled independently in settings. If a message contains multiple qualifying digit runs, the first one is used for the code-only follow-up.

## Trusted shortcode relay

The app also supports a trusted-controller relay for interacting with six-digit SMS short codes through the forwarding phone.

- The existing destination number is the trusted controller by default; a different controller number can be configured.
- Only that trusted number can issue relay commands.
- A command is a bracketed six-digit target followed by the SMS body, for example:

```text
[711711] Y
```

- The app strips the bracketed shortcode and surrounding whitespace, sends only `Y` to shortcode `711711`, and opens a 5-minute reply window for that shortcode.
- During that window, messages received from `711711` are forwarded to the trusted controller as:

```text
[711711] <reply text>
```

- Sending another `[711711] ...` command restarts the 5-minute window. `[711711]` with no payload can be used just to open a reply window.
- Only one shortcode session is active at a time; a command for a different shortcode replaces the previous session.
- The relay feature can be disabled independently of normal OTP forwarding.
- The active session stores only the shortcode and expiration timestamp, not message contents.

Carrier/device policy can still reject outbound messages to some short codes; the app reports a generic send failure if Android rejects the operation synchronously.

## Privacy / security

- Uses only `RECEIVE_SMS` and `SEND_SMS` runtime permissions.
- Does **not** request `READ_SMS` or Internet access.
- Does not scan SMS history.
- Does not store message bodies or verification codes.
- Stores only destination/filter/follow-up/relay/enabled settings, active shortcode + expiry, and a short status string.
- Prefixes the full relayed OTP message with `[SMS Forwarder]` and ignores already-prefixed messages to prevent forwarding loops.
- The optional code-only copy is an additional outbound SMS, so normal carrier SMS charges can apply.

## Build

Requires Java 17-compatible Android build tooling and Android SDK 35.

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
4. Leave **Only forward messages containing 6+ consecutive digits** checked for the default code-only filter.
5. Leave **Also send the extracted code as a second SMS for easy copying** checked if you want the code-only follow-up.
6. Leave **Enable trusted [123456] shortcode relay** checked if you want remote shortcode interaction; optionally set a separate trusted controller number.
7. Enable normal forwarding if desired and save.

The app receives only new messages after installation. Android may show warnings because SMS permissions are sensitive; this project is intended for personal sideloading rather than Play Store distribution.

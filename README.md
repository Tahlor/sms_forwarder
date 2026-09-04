# SMS Code Forwarder

A tiny sideload-only Android app for forwarding newly received SMS messages to another phone number.

By default it forwards only messages containing **6 or more consecutive digits** (`[0-9]{6,}`), which catches most verification/2FA codes without forwarding ordinary texts. The filter can be disabled in the app to forward all newly received SMS messages.

By default, when a forwarded message contains such a code, the app also sends a **second SMS whose entire body is just the extracted digit sequence**. This makes the code easy to copy even when Android/Google Messages does not recognize the forwarded message as an OTP. The extra code-only SMS can be disabled independently in settings. If a message contains multiple qualifying digit runs, the first one is used for the code-only follow-up.

## OTP-safe shortcode display

Six-digit sender/shortcode labels are displayed with a dash so they do not look like a second six-digit verification code. For example, sender `711711` is shown as `711-711` in a forwarded message. The raw destination remains `711711` when the app actually sends an SMS.

This also applies to trusted-relay labels. The preferred command form is `[711-711] Y`; the older `[711711] Y` form remains accepted for compatibility. Replies are rendered as `[711-711] <reply>`.

## Trusted shortcode relay

The app supports a trusted-controller relay for interacting with six-digit SMS short codes through the forwarding phone.

- The existing destination number is the trusted controller by default; a different controller number can be configured.
- Only that trusted number can issue relay commands.
- A command is a bracketed six-digit target (optionally dashed 3-3) followed by the SMS body, for example:

```text
[711-711] Y
```

- The app strips the bracketed shortcode and surrounding whitespace, sends only `Y` to raw shortcode `711711`, and opens a 5-minute reply window for that shortcode.
- During that window, messages received from `711711` are forwarded to the trusted controller as `[711-711] <reply text>`.
- Sending another command restarts the 5-minute window. A shortcode marker with no payload can be used just to open a reply window.
- Only one shortcode session is active at a time; a command for a different shortcode replaces the previous session.
- The relay feature can be disabled independently of normal OTP forwarding.
- The active session stores only the shortcode and expiration timestamp, not message contents.

Carrier/device policy can still reject outbound messages to some short codes; the app reports a generic send failure if Android rejects the operation synchronously.

## Updating the app

The settings screen has an **Update app** button. It opens this stable URL in the device browser:

```text
https://taylorarchibald.com/apks/sms-code-forwarder-latest.apk
```

Archimedes must publish the newest verified APK at that URL using the **same persistent signing key as every prior canonical build**, so Android can install it as an in-place update. The forwarder itself does not download the APK and therefore does not need Internet permission; Android hands the URL to the browser via `ACTION_VIEW`.

### Signing/update invariant

Android updates require all of the following:

1. the same application ID (`com.tahlor.smsforwarder`);
2. the same signing certificate as the installed canonical build;
3. a strictly higher `versionCode` for each newer release.

The repository now enforces explicit signing for release builds. `assembleRelease`/`bundleRelease` fail unless the persistent signer is configured through either `local.properties`:

```properties
release.storeFile=/absolute/path/to/sms-forwarder-release.jks
release.storePassword=...
release.keyAlias=sms-forwarder
release.keyPassword=...
```

or the equivalent environment variables:

```text
SMS_FORWARDER_KEYSTORE
SMS_FORWARDER_STORE_PASSWORD
SMS_FORWARDER_KEY_ALIAS
SMS_FORWARDER_KEY_PASSWORD
```

The keystore and passwords must never be committed. Archimedes should keep the keystore in durable private host storage, record its SHA-256 certificate fingerprint in the deployment report, and verify every newly published APK has that same fingerprint before replacing the stable latest URL.

If an older installed copy was signed by a different/unknown key, migration requires **one uninstall/reinstall**: uninstall the old copy, install the canonical persistent-key APK, and then retain that signing lineage forever. Do not migrate users onto a temporary/debug signer merely to make one installation succeed.

Do not point the update button at the GitHub Actions debug artifact unless its signer is deliberately made identical to the canonical signer. A mismatched signer cannot update an existing installation.

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

For normal CI/debug validation:

```bash
gradle testDebugUnitTest assembleDebug
```

For a canonical publishable build on Archimedes, configure the persistent signer above and run:

```bash
gradle testDebugUnitTest assembleRelease
```

The publishable release APK is under `app/build/outputs/apk/release/`. Verify its package, version, SHA-256, permissions, and signing certificate fingerprint before publishing it.

GitHub Actions builds every push to `master` and uploads a debug APK as test/build evidence. It is not the canonical update artifact unless explicitly configured with the same persistent signer.

## Install / configure

1. Sideload the APK on the phone receiving the source SMS messages.
2. Open **SMS Code Forwarder** and grant SMS receive/send permissions.
3. Enter the destination phone number.
4. Leave **Only forward messages containing 6+ consecutive digits** checked for the default code-only filter.
5. Leave **Also send the extracted code as a second SMS for easy copying** checked if you want the code-only follow-up.
6. Leave **Enable trusted [123-456] shortcode relay** checked if you want remote shortcode interaction; optionally set a separate trusted controller number.
7. Enable normal forwarding if desired and save.
8. Use **Update app** later to open the stable latest-APK URL.

The app receives only new messages after installation. Android may show warnings because SMS permissions are sensitive; this project is intended for personal sideloading rather than Play Store distribution.

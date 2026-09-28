# SMS Forwarder

A sideload-only Android app for forwarding newly received SMS messages to trusted downstream phones and for sending bracket-addressed SMS commands back through the forwarding phone.

Version **0.2.0 / versionCode 12** separates security authorization from automatic forwarding preferences, adds carrier-result acknowledgements for relay commands, adds privacy-safe runtime diagnostics, and follows the Android system light/dark theme.

## Security model

Each downstream phone now has separate **capabilities** and **preferences**.

### Incoming security authorization

This is the hard privacy boundary. A phone can be authorized for:

- **No incoming access**
- **Any sender**
- **Selected senders only**

A sender on the incoming block list is always denied. Automatic forwarding can never expand this authorization.

### Automatic forwarding preference

Inside the incoming authorization, choose:

- **Forward nothing**
- **All authorized messages**
- **Security codes only**
- **Selected authorized senders**

Effective delivery is always:

```text
incoming security authorization ∩ automatic forwarding preference
```

This supports both important cases:

- a fully trusted second phone can be authorized for **Any sender** while automatically receiving only **Security codes**;
- a less-trusted phone can be authorized for only specific senders, and no broader forwarding preference can escape that boundary.

A new phone defaults to **No incoming access** and **No outgoing access**. Broad capabilities are never silently granted.

### Security-code detection

Security-code mode currently matches a run of 6 or more consecutive ASCII digits:

```text
[0-9]{6,}
```

If **Send detected code separately for easy copying** is enabled, the app forwards the full authorized message and also sends the first matching digit run as a second SMS.

### Outgoing relay authorization

This is also a security capability, granted independently for each downstream phone:

- **No outgoing access**
- **Short codes only** — normalized destinations with 3–6 digits
- **Selected numbers only**
- **Any number** — any normalized 3–15 digit destination

The outgoing block list always wins.

## Command reference

A relay command must come from a configured downstream phone and begin with a bracketed destination.

Short code:

```text
[711711] SAVE
```

Normal phone number:

```text
[8552448147] SAVE
```

The forwarding phone strips the bracketed destination and sends only the payload.

Common punctuation is ignored while normalizing the destination. The normalized destination must contain 3–15 digits.

An empty payload opens a reply window without sending an SMS:

```text
[8552448147]
```

### Relay acknowledgement

For a non-empty relay command, the app waits for Android's carrier send callback. The downstream/controller phone receives an acknowledgement such as:

```text
[SMS Forwarder]
Sent to 8552448147. Replies will return for 5 minutes.
```

or a failure acknowledgement.

Blocked commands are also acknowledged when SEND_SMS permission is available.

## Reply window

After a relay is reported successfully sent, the app keeps a 5-minute return route for that downstream phone. An explicit empty command opens the same window immediately.

A reply from the destination is returned to the downstream phone as:

```text
[8552448147] <reply text>
```

Sending another command refreshes the 5-minute conversation window.

## Multiple downstream phones

Every phone has independent:

- incoming security authorization;
- incoming authorized and blocked sender lists;
- automatic forwarding preference;
- optional automatic-forwarding sender list;
- code-only copy preference;
- outgoing relay authorization;
- outgoing allow/block lists;
- temporary reply window.

Editing a phone number replaces its original profile rather than leaving a duplicate.

## In-app Help and diagnostics

The **Help** page is the canonical command/behavior reference. It includes:

- exact command syntax and examples;
- current permission/profile setup;
- security authorization versus forwarding preference;
- security-code matching behavior;
- outgoing capabilities;
- 5-minute reply behavior;
- sideloaded permission instructions;
- troubleshooting;
- recent runtime activity.

The runtime activity log is intentionally privacy-limited. It stores routing/result descriptions and addressing metadata only. It does **not** persist message bodies or verification codes, and it is stored outside the backed-up preferences file.

A received SMS now records an **Incoming SMS received** event before filtering. This makes it possible to distinguish:

1. Android never delivered the SMS broadcast to the app;
2. the app received it but security/preference rules rejected it;
3. forwarding was queued;
4. Android later reported a send success/failure.

## Sideload authorization

The app requests only:

- `RECEIVE_SMS`
- `SEND_SMS`

It does **not** request `READ_SMS` and has no Internet permission.

On Android 13+, a sideloaded app can have SMS permissions blocked by Restricted Settings. The app guides:

1. Open App Info.
2. Tap the top-right `⋮` menu.
3. Choose **Allow restricted settings**.
4. Return to SMS Forwarder.
5. Retry the Android SMS permissions.

## Appearance and navigation

The app follows Android's system Light/Dark theme by default, including cards, editor surfaces, Help, dialogs/native controls, and status/navigation bar icon contrast.

Android 15+ edge-to-edge insets are applied so the app content stays clear of the status bar, display cutout, and bottom navigation/gesture area.

On the main screen:

- tapping a phone card or its visible **Edit ›** action opens the editor;
- Back from the editor returns to the setup screen before leaving the app;
- Back also collapses the expanded More panel first.

## Settings migration

Existing profiles are migrated conservatively without widening their old behavior:

- old **All messages** → authorization **Any sender** + preference **All authorized messages**;
- old **Security codes only** → authorization **Any sender** + preference **Security codes only**;
- old **Selected senders only** → authorization **Selected senders** using the old allow list + preference **All authorized messages**;
- old **Nothing** → no incoming authorization + forwarding off;
- existing incoming block lists remain hard blocks;
- existing outgoing modes and allow/block lists retain their meaning.

The next save writes the new model. Profile preferences participate in Android backup/restore. Runtime status, diagnostic activity, delivery tracking, and active reply sessions do not.

## Privacy / security

- No `READ_SMS`.
- No Internet permission.
- No SMS-history scan.
- No message bodies or verification codes persisted by diagnostics.
- Full forwarded messages use `[SMS Forwarder]`; already-prefixed acknowledgement messages are ignored to prevent loops.
- Relay commands execute only for a sender matching a configured downstream phone.
- Incoming delivery is authorization ∩ preference.
- Incoming and outgoing block lists take precedence over allows/preferences.
- New phones start with no security capabilities granted.

## Build

Requires Java 17-compatible Android build tooling and Android SDK 35.

```bash
gradle testDebugUnitTest assembleDebug
```

Canonical releases use the persistent signer:

```bash
gradle testDebugUnitTest assembleRelease
```

## Automated Archimedes deployment

The Archimedes deployment is intentionally gated and repeatable:

1. the host-side bridge fast-forwards a **clean** `master` checkout;
2. `scripts/deploy_archimedes.sh` runs tests and the signed release build;
3. it verifies package ID, version, and the persistent signer before publishing;
4. the signed APK is published to the phone-share handoff and to
   `/var/www/html/apks/sms-code-forwarder-latest.apk`.

The deployment script deliberately refuses dirty or divergent checkouts and refuses to publish a version code that is not newer than the public APK.

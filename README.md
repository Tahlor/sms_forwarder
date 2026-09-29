# SMS Forwarder

A sideload-only Android app for forwarding newly received SMS messages to trusted downstream phones and for sending bracket-addressed SMS commands back through the forwarding phone.

Version **0.3.1 / versionCode 16** separates security authorization from automatic forwarding preferences, adds carrier-result acknowledgements for relay commands, integrates opt-in SMS remote management, broadens verification-code recognition, adds privacy-safe runtime diagnostics, and follows the Android system light/dark theme.

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
- **Security codes only** — detected codes plus any explicitly preferred/ALLOW sender
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

Security-code mode recognizes common verification messages using two paths:

- authentication language such as `code`, `verification`, `OTP`, `PIN`, `login`, `security`, or do-not-share wording together with a 4–8 digit token;
- formatted codes such as `123-456` or `123 456`, including prefixed forms such as `G-123456`;
- for backward compatibility, any run of 6 or more consecutive digits still qualifies.

The app always forwards the full authorized original message. The forwarding envelope is compact (`[FWD] sender`) so common OTP messages, including the observed Credit Karma WebOTP, stay within one SMS segment when possible. If **Send detected code separately for easy copying** is enabled, the app additionally sends exactly one code-only SMS with separators removed. Equivalent duplicate destination profiles are suppressed per incoming event so one source message cannot generate duplicate code-only sends to the same phone.

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

## RCS command compatibility

Carrier SMS remains the canonical command transport. Google Messages can switch a conversation back to RCS, in which case `SMS_RECEIVED` never sees commands such as `ALLOW 711711`.

Version 0.3.1 adds an **opt-in Google Messages notification bridge**:

- the host user explicitly grants Android Notification Access;
- only Google Messages notifications are inspected;
- the app considers only text that matches the existing command grammar;
- a command is executed only when Android exposes a sender `Person` URI with a phone-number scheme and that number matches a configured downstream phone;
- contact/display names alone are never trusted;
- reposted notifications are deduplicated;
- notification/RCS message bodies are not persisted;
- the existing SMS command path works exactly as before even when Notification Access is off.

This is a compatibility layer over conversation notifications, not direct access to the Google Messages RCS database.

## Remote management by SMS

Remote management is a separate per-phone security capability and is **off by default**. Enable it only for a downstream phone you fully trust. When enabled, that phone can change its own incoming security and automatic-forwarding rules by texting the host phone.

Commands may optionally begin with `CMD `.

```text
AUTH ANY
AUTH SELECTED
AUTH OFF
```

Changes the hard incoming security authorization.

```text
ALLOW 711711
UNALLOW 711711
```

`ALLOW` is the convenient "always forward this sender" shortcut: it puts the sender inside the hard authorization and the always-forward/preferred list. In Security codes mode, that sender's non-code messages also forward. If automatic forwarding is Off, ALLOW switches it to Selected. `UNALLOW` removes both entries.

```text
BLOCK 711711
UNBLOCK 711711
```

Adds/removes a hard block. Blocks always win.

```text
MODE CODES
MODE ALL
MODE SELECTED
MODE OFF
```

Changes automatic forwarding only. It never expands the hard security authorization.

```text
PREFER 711711
UNPREFER 711711
```

Changes the sender list used by `MODE SELECTED`. A sender must already be inside the hard authorization and not blocked.

```text
PING
LIST
STATUS
HELP
```

`PING` is read-only and works even when remote management is disabled. It proves that the command arrived as carrier SMS and that the app can send a reply. `LIST`, `STATUS`, and `HELP` require remote management because they expose/manage the profile.

Remote commands must arrive as **SMS**, not RCS/chat. If PING produces no response and no Recent activity entry, force the message to send as SMS before debugging the rule parser.

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
- temporary reply window;
- opt-in remote-management capability.

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
- recent runtime activity;
- remote-management command reference and security implications.

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
- existing outgoing modes and allow/block lists retain their meaning;
- remote management defaults to **off** for migrated profiles unless explicitly enabled in a newer saved profile.

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
- New phones start with no incoming, outgoing, or remote-management capability granted.
- Remote management is opt-in and can change the phone's own incoming security/forwarding rules, so it should be enabled only for a fully trusted downstream phone.

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


## Android 17 OTP delivery limitation

On Android 17, protected OTP messages can be withheld from ordinary non-default SMS apps before `SMS_RECEIVED_ACTION` is delivered. WebOTP-style messages such as:

```text
@creditkarma.com #571782
```

can therefore be delayed before SMS Forwarder sees them, even when this app's own rules would forward them.

Android's Android 17 behavior-change documentation explicitly lists **connected-device companion apps** among the apps exempt from the OTP delay. Version 0.3.0 adds a real Android `CompanionDeviceManager` association flow:

1. On the other phone, open **Bluetooth → Pair new device** and leave it discoverable.
2. On the forwarding phone, open SMS Forwarder.
3. Under **Companion protection**, tap **Pair companion phone**.
4. Choose the other phone in Android's system companion-device chooser.
5. The main screen and Help page report whether this package has an active system companion association.

This is intentionally additive. Companion association does **not** modify or replace:
- configured downstream phone profiles;
- incoming security authorization;
- automatic-forwarding preferences;
- ALLOW/BLOCK/MODE/PREFER rules;
- outgoing relay permissions;
- remote management;
- SMS permissions;
- PING or bracket relay commands.

Removing the Android companion association also leaves all SMS Forwarder profiles and rules intact.

The exact Credit Karma/WebOTP behavior still requires device verification after association; the app does not claim success merely because an association exists. The Help screen's rules self-test and Recent activity make it possible to distinguish "our rules would forward it" from "Android never delivered the protected SMS broadcast."

SMS Forwarder intentionally does not make itself the default SMS app. As a consequence, it also cannot prevent the default Messages app from storing/notifying on command SMS or mark those messages read.


## 0.3.1 forwarding reliability

The Credit Karma reproduction exposed a multipart edge case: the old `[SMS Forwarder]\nFrom: ...` envelope could push a roughly 129-character OTP message over the 160-character single-SMS boundary, causing the full forward to take the multipart path while the short code-only copy succeeded. 0.3.1 shortens the envelope to `[FWD] sender\n`, preserves recognition of the legacy marker for loop prevention, reports the number of queued full-message parts, and suppresses equivalent duplicate downstream destinations within one receive event.

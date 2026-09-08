# SMS Forwarder

A small sideload-only Android app that forwards newly received SMS messages to one or more downstream phones and can optionally send bracket-addressed replies back through the forwarding phone.

## Setup model

Each downstream phone is configured once with two independent policies.

### Incoming messages → downstream

Choose one mode:

- **All messages**
- **Secure codes only** — messages containing 6+ consecutive digits (`[0-9]{6,}`)
- **Selected senders only** — only senders on the incoming allow list
- **Nothing**

Incoming **Sender rules** provide both an allow list and a block list. The allow list is used by Selected senders only. The block list is enforced in every enabled mode and always wins.

A profile can also send a second SMS containing only the first detected security code for easier copying.

### Downstream → outgoing SMS

Choose one mode:

- **Any number**
- **Short codes only**
- **Selected numbers only** — only destinations on the outgoing allow list
- **Nothing**

Outgoing **Destination rules** likewise provide an allow list and a block list. The block list is always enforced and always wins.

To send through the forwarding phone, the downstream phone sends a bracket-addressed SMS to the forwarding phone:

```text
[711711] SAVE
```

or, when normal numbers are allowed:

```text
[8015551234] Sounds good, see you at 7
```

The forwarding phone strips the bracketed destination and sends the payload to that destination.

Phone-number rules normalize common formatting differences such as `+1`, spaces, parentheses, and dashes. Incoming alphanumeric sender IDs can also be matched exactly, case-insensitively.

## Reply window

After an allowed bracket-addressed outgoing command, the app keeps a 5-minute return route for that downstream phone. A reply from the destination is sent back to the same downstream phone as:

```text
[711-711] <reply text>
```

or the equivalent bracketed normal phone number.

Sending another command refreshes the 5-minute window. A bracketed destination with no payload opens the return window without sending an SMS.

## Multiple downstream phones

Multiple downstream phones are supported. Each phone has independent:

- incoming mode;
- incoming allow/block lists;
- code-only copy preference;
- outgoing mode;
- outgoing allow/block lists;
- temporary reply window.

Editing a downstream phone number replaces the original profile rather than accidentally leaving a duplicate behind.

## Sideload authorization

The app requests only `RECEIVE_SMS` and `SEND_SMS`. On Android 13+, sideloaded apps can have sensitive permissions blocked by Restricted Settings. The app uses one guided **Authorize SMS access** flow:

1. request Receive SMS and Send SMS;
2. if Android blocks them, explain App Info → top-right `⋮` → **Allow restricted settings**;
3. automatically retry the SMS permission request when the user returns.

Android does not expose a public API that lets an app silently enable Allow restricted settings itself.

## UI

The main screen is organized around the two directions instead of a grid of feature checkboxes:

- compact SMS authorization status;
- downstream phone selector/editor;
- **Incoming messages → downstream** dropdown;
- collapsible **Sender rules**;
- **Downstream → outgoing SMS** dropdown;
- collapsible **Destination rules**;
- help/update/maintenance actions.

The **Examples & help** page shows the current setup, permission state, runtime status, forwarding examples, reply syntax, filtering behavior, and restricted-settings instructions.

## Settings migration and persistence

Existing profile settings are read compatibly:

- legacy forwarding + code-only booleans map to All messages / Secure codes only / Nothing;
- legacy shortcode relay maps to Short codes only / Nothing;
- existing downstream phone numbers and code-copy preferences are retained.

New allow/block lists and directional modes participate in Android backup/restore. Runtime status and active 5-minute reply sessions remain transient and are not backed up.

**Delete saved setup** clears all downstream phones, directional rules, and runtime state. It does not revoke Android SMS permissions.

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
- Full forwarded messages use `[SMS Forwarder]`; already-prefixed messages are ignored to prevent forwarding loops.
- Outgoing relay requires the sender to match a configured downstream phone and its destination policy.
- Block lists take precedence over allow lists.

## Build

Requires Java 17-compatible Android build tooling and Android SDK 35.

```bash
gradle testDebugUnitTest assembleDebug
```

Canonical release builds use the persistent signer and:

```bash
gradle testDebugUnitTest assembleRelease
```

Version: **0.1.6 / versionCode 7**.

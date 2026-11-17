# SMS Link Automator - security and behavior specification

Independent Android sideload app in this repository: `:linkautomator` (package `com.tahlor.smslinkautomator`). It does not change the installed SMS Forwarder app or its connected flavor.

## Configuration: two independent per-sender controls

* **Always follow eligible links**: off by default; while on, every new SMS from the exact configured sender is checked for HTTPS links, and every link with an exact approved hostname is automatically executed, whether or not the SMS contains any configured keywords.
* **Auto-arm after heads-up**: off by default; while enabled, the receiver always watches for the configured phrase from the configured sender, even if Always is off. A case-insensitive substring match sets a timed active window. Default length 60 minutes, configurable 1-1440 minutes. Messages arriving strictly before expiry can execute eligible links. After expiry they cannot. A further matching heads-up restarts the clock.
* **The trigger SMS is not itself followed in timed-only mode**, even if it contains a link. A manual Always-on rule follows eligible URLs, including those in a trigger SMS.
* The two switches are independent. Disabling Always does not disable a currently active auto-arm window. Disabling Auto-arm cancels that window. A dedicated Disarm timed window button cancels the window without changing either switch. A rule can have both controls enabled simultaneously.
* Reboot does not reset the persisted timeout. Clock jumps and stale/invalid timeout state fail closed. Android may not deliver broadcasts to a force-stopped app.
* Each rule applies to an exact sender, not to all SMS senders. More rules can be added. Identical URLs are deduplicated across matching rules in one message.

## Included example (all actions initially disabled)

* Name: Quikly (example)
* Sender: `34653`
* Heads-up phrase: `heads-up` (case-insensitive substring)
* Auto-arm duration: 60 minutes
* Approved HTTPS hostname: `claim.quikly.com` (exact match, no wildcard)
* Execution mode: background HTTPS GET

With auto-arm enabled, the heads-up message arms the window. A **later SMS from 34653** with `https://claim.quikly.com/11785/...` automatically triggers the request during that window. No claim-language matcher is required for the second SMS. SMS from other senders or links on other domains are never followed.

## Link execution semantics, limitations, and security

* Only HTTPS allowed, no userinfo, fragments, or nonstandard ports; max 2048 chars per URL and 3 eligible URLs per rule per SMS.
* Approved hosts are explicitly specified by the user as exact DNS names (up to 10 per rule). IP literals, localhost/internal names, wildcards, and scheme-less URLs are rejected.
* Every HTTP redirect is revalidated against the approved host list. Redirects to unapproved hosts are **not followed**. Requests use short network timeouts and never store the message body, full URL, query token, or response body. Recent activity stores only operational descriptions and allowed host names.
* HTTP mode initiates an HTTPS GET in the background, without JavaScript, browser cookies, or web form submission. A 200 response means an HTTP request was received, **not that a promotion was claimed**. Some sites require browser sessions or JavaScript; those are not supported by HTTP mode.
* Optional browser mode issues `ACTION_VIEW` without a tap. On modern Android the operating system may silently block an activity launch from the background. Browser launch requested is explicitly logged as **not verified**; it must not be mistaken for a successful click or claim. There is no notification fallback.
* Network requests are made only after sender authorization, URL authorization, and active-rule checks pass. Users should enable automatic requests only for trusted domains and consider applicable promotion rules.
* Deduplication of the same SMS sender/timestamp/body is stored as a SHA-256 fingerprint for 24 hours and committed before any requests. No original message or token is persisted.
* No default SMS role, `READ_SMS`, `SEND_SMS`, Accessibility permission, overlay permission, account, background server, or SMS exfiltration.
* SMS receipt requires sideload-granted `RECEIVE_SMS`; Google Play heavily restricts SMS permissions. Android can delay or suppress some messages. Real-device permission and background-delivery testing remains necessary.

## Developer/build checks

On a machine with Android SDK 35, JDK 17, Gradle 8.9:

```sh
gradle :linkautomator:testDebugUnitTest :linkautomator:assembleDebug :linkautomator:lintDebug
```

APK output: `linkautomator/build/outputs/apk/debug/linkautomator-debug.apk`. The existing GitHub Actions Android APK workflow builds/tests this module and uploads its debug APK alongside the original two editions.

## Acceptance checklist

1. Both toggles off: no link visit and no arming.
2. Always on with auto-arm off: eligible link from approved sender visited immediately; no keyword needed.
3. Always off with auto-arm on: prior to heads-up, link ignored; heads-up arms; link on later SMS visits; at expiry link ignored.
4. Trigger contains a link: only arms; does not visit unless Always is also on.
5. Repeated heads-up resets expiry from the latest trigger; manual disarm and disabling auto-arm stop timed visits.
6. Unknown sender, wrong host, HTTP link, deceptive subdomain, IP literal, disallowed redirect, or duplicate SMS: no request.
7. Test unlocked, locked, screen off, and after reboot on the Pixel 11. Verify execution by a controlled test endpoint rather than trusting an HTTP status or browser-launch request. Background browser mode is not guaranteed.

## Hub 2 / APK Library delivery

The canonical release artifact is a **persistently signed** release APK, not the GitHub debug artifact. On Archimedes the separate guarded publisher `scripts/deploy_linkautomator.sh` builds, verifies package/version/signature, and atomically publishes both a versioned APK and `sms-link-automator-latest.apk` under `/home/ubuntu/phone_share/apks/sms_link_automator/`. Hub 2's existing APK tile points to the SSO-protected `/apk_downloads/` service, which reads that directory; no duplicate web service is needed.

Once installed, `sms-link-automator-deploy.timer` checks new `master` revisions every five minutes. Commits that do not change this Android module do not cause a rebuild. A changed module requires a higher version code. The legacy SMS Forwarder publisher separately detects when its module is unchanged. Never bypass signature checking or promote CI debug artifacts as the latest release.

**Direct authenticated download:** https://taylorarchibald.com/apk_downloads/download/sms_link_automator/sms-link-automator-latest.apk

## Built-in testing

Tap **Run one-tap self-test** on the home screen. It runs automatically with the built-in sample, requires no configuration or SMS permission, and displays 12 PASS/FAIL checks covering sender matching, arming, link allowlisting and expiry. It never transmits SMS or follows a real URL, so it cannot verify radio delivery or a promotion claim. Open a rule and tap **Test rule safely** to test that rule specifically. The offline flow verifies blocked-before-heads-up, timed arming, suppression of links in the triggering text, subsequent-link eligibility, automatic expiry, approved/denied hosts, and Always-on independence. This never sends SMS, visits a URL or changes live settings.

Optional **Visit entered URL once** makes a real request only after you supply a full URL belonging to the rule's approved host list. Use a harmless test endpoint—not an actual giveaway link.

For full radio-path testing, add a temporary rule with the sender number of **another phone you control** and a harmless HTTPS host. Send the heads-up and then the link SMS from that phone; verify the live activity log. Self-SMS delivery may never reach the received-SMS broadcast, and your own sender cannot impersonate the real `34653` shortcode.

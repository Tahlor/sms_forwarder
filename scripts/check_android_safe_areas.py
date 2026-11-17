#!/usr/bin/env python3
"""Reject new Android activities that forget status, cutout, and nav-bar insets."""
from pathlib import Path

roots = (Path("app/src"), Path("linkautomator/src/main/java"))
errors = []
checked = 0
for root in roots:
    for source in root.rglob("*.java"):
        # This is a lightweight source guard, not a substitute for device UI checks.
        text = source.read_text(encoding="utf-8")
        if "extends Activity" not in text or "setContentView(" not in text:
            continue
        checked += 1
        if "SystemBars.configure(" not in text and "EdgeToEdge.apply(" not in text:
            errors.append(str(source))
if checked == 0:
    raise SystemExit("No Android Activity sources found")
if errors:
    raise SystemExit("Activities without system-bar/display-cutout insets:\n" + "\n".join(errors))
print(f"Verified safe-area handling in {checked} Activity screens")

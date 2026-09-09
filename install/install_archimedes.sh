#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ "$(hostname -s)" == "archimedes" ]] || {
    printf 'This installer is only for archimedes.\n' >&2
    exit 1
}

UNIT_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
install -d -m 700 "$UNIT_DIR"
install -m 0644 "$ROOT/install/sms-forwarder-deploy.service" "$UNIT_DIR/sms-forwarder-deploy.service"
install -m 0644 "$ROOT/install/sms-forwarder-deploy.timer" "$UNIT_DIR/sms-forwarder-deploy.timer"

systemctl --user daemon-reload
systemctl --user enable --now sms-forwarder-deploy.timer

printf 'Installed and enabled sms-forwarder-deploy.timer.\n'
printf 'Run now: systemctl --user start sms-forwarder-deploy.service\n'
printf 'Logs:    journalctl --user -u sms-forwarder-deploy.service -n 200 --no-pager\n'

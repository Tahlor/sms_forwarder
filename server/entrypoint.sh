#!/bin/sh
set -e

DATA_DIR="${SMS_BRIDGE_DATA_DIR:-/data}"
mkdir -p "$DATA_DIR"
chown -R smsbridge:smsbridge "$DATA_DIR" 2>/dev/null || true

exec gosu smsbridge "$@"

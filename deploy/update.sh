#!/usr/bin/env bash
# Installs a new big2-server.jar (uploaded next to this script) and restarts the game.
# Games in progress end when the server restarts.
#
# Usage: sudo bash update.sh
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "Please run with sudo: sudo bash update.sh"
  exit 1
fi
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[[ -f "$HERE/big2-server.jar" ]] || { echo "Missing $HERE/big2-server.jar"; exit 1; }

install -o big2 -g big2 -m 644 "$HERE/big2-server.jar" /opt/big2/big2-server.jar
systemctl restart big2
sleep 3
if curl -fsS http://127.0.0.1:8090/health >/dev/null; then
  echo "Updated and running."
else
  echo "The game did not come back up. See: sudo journalctl -u big2 -n 50"
  exit 1
fi

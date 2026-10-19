#!/usr/bin/env bash
# One-time setup of Big 2 on an Ubuntu server (Tencent Lighthouse or any VPS).
# Installs Java 21 and Caddy, runs the game as a service, and serves it on your domain over HTTPS.
#
# Usage (big2-server.jar and big2.service must sit next to this script):
#   sudo bash setup.sh big2.example.com
# Safe to run again, for example to change the domain.
set -euo pipefail

DOMAIN="${1:-}"
if [[ -z "$DOMAIN" ]]; then
  echo "Usage: sudo bash setup.sh big2.example.com"
  exit 1
fi
if [[ $EUID -ne 0 ]]; then
  echo "Please run with sudo: sudo bash setup.sh $DOMAIN"
  exit 1
fi

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
for f in big2-server.jar big2.service; do
  [[ -f "$HERE/$f" ]] || { echo "Missing $HERE/$f. Upload it next to this script first."; exit 1; }
done

echo "==> Installing Java 21"
apt-get update -y
apt-get install -y openjdk-21-jre-headless curl gnupg debian-keyring debian-archive-keyring apt-transport-https

echo "==> Installing Caddy (web server with automatic HTTPS)"
if ! command -v caddy >/dev/null 2>&1; then
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
    | gpg --batch --yes --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
    > /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -y
  apt-get install -y caddy
fi

echo "==> Installing the game as a service"
id big2 >/dev/null 2>&1 || useradd --system --home-dir /opt/big2 --shell /usr/sbin/nologin big2
install -d -o big2 -g big2 /opt/big2
install -o big2 -g big2 -m 644 "$HERE/big2-server.jar" /opt/big2/big2-server.jar
install -m 644 "$HERE/big2.service" /etc/systemd/system/big2.service
systemctl daemon-reload
systemctl enable big2
systemctl restart big2

echo "==> Pointing $DOMAIN at the game"
cat > /etc/caddy/Caddyfile <<EOF
$DOMAIN {
    encode gzip
    reverse_proxy 127.0.0.1:8090
}
EOF
systemctl enable caddy
systemctl restart caddy

if command -v ufw >/dev/null 2>&1 && ufw status | grep -q "Status: active"; then
  echo "==> Opening ports 80 and 443 in ufw"
  ufw allow 80/tcp
  ufw allow 443/tcp
fi

sleep 3
if systemctl is-active --quiet big2 && curl -fsS http://127.0.0.1:8090/health >/dev/null; then
  echo
  echo "Big 2 is running. Open https://$DOMAIN"
  echo "(The first visit can take a few seconds while Caddy gets the HTTPS certificate.)"
else
  echo
  echo "The game service did not start. See: sudo journalctl -u big2 -n 50"
  exit 1
fi

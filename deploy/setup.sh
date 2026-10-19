#!/usr/bin/env bash
# One-time setup of Big 2 on an Ubuntu server, behind nginx with a Let's Encrypt certificate.
# Works alongside other nginx sites: it only adds its own site file for your domain.
#
# Usage, from the deploy folder (big2-server.jar must sit next to this script):
#   sudo bash setup.sh big2.example.com
# Safe to run again.
set -euo pipefail

DOMAIN="${1:-}"
PORT=18090
SITE_NAME=big2

if [[ -z "$DOMAIN" ]]; then
  echo "Usage: sudo bash setup.sh big2.example.com"
  exit 1
fi
if [[ $EUID -ne 0 ]]; then
  echo "Please run with sudo: sudo bash setup.sh $DOMAIN"
  exit 1
fi

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
for f in big2-server.jar big2.service nginx-big2.conf; do
  [[ -f "$HERE/$f" ]] || { echo "Missing $HERE/$f"; exit 1; }
done

if ss -ltn | grep -q ":$PORT " && ! systemctl is-active --quiet big2; then
  echo "Port $PORT is already used by another program. Change it in big2.service, nginx-big2.conf and here."
  exit 1
fi

echo "==> Installing Java 21, nginx and certbot (already-installed ones are kept)"
apt-get update -y
apt-get install -y openjdk-21-jre-headless nginx certbot python3-certbot-nginx curl

echo "==> Installing the game as a service on 127.0.0.1:$PORT"
id big2 >/dev/null 2>&1 || useradd --system --home-dir /opt/big2 --shell /usr/sbin/nologin big2
install -d -o big2 -g big2 /opt/big2
install -o big2 -g big2 -m 644 "$HERE/big2-server.jar" /opt/big2/big2-server.jar
install -m 644 "$HERE/big2.service" /etc/systemd/system/big2.service
systemctl daemon-reload
systemctl enable big2
systemctl restart big2

for _ in $(seq 1 20); do
  curl -fsS "http://127.0.0.1:$PORT/health" >/dev/null 2>&1 && break
  sleep 1
done
if ! curl -fsS "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; then
  echo "The game did not start. See: sudo journalctl -u big2 -n 50"
  exit 1
fi
echo "    game is running"

echo "==> Adding the nginx site for $DOMAIN"
OTHER=$(grep -rlE "server_name[^;]*[[:space:]]$DOMAIN[[:space:];]" /etc/nginx/sites-enabled/ 2>/dev/null | grep -v "/$SITE_NAME\$" || true)
if [[ -n "$OTHER" ]]; then
  echo "Another nginx site already uses $DOMAIN: $OTHER"
  echo "Remove $DOMAIN from it first, then run this again."
  exit 1
fi
if [[ -f "/etc/nginx/sites-available/$SITE_NAME" ]]; then
  echo "    keeping the existing /etc/nginx/sites-available/$SITE_NAME (it may hold certbot's HTTPS settings)"
else
  sed -e "s/__DOMAIN__/$DOMAIN/g" -e "s/__PORT__/$PORT/g" "$HERE/nginx-big2.conf" > "/etc/nginx/sites-available/$SITE_NAME"
fi
ln -sf "/etc/nginx/sites-available/$SITE_NAME" "/etc/nginx/sites-enabled/$SITE_NAME"
if ! nginx -t; then
  rm -f "/etc/nginx/sites-enabled/$SITE_NAME"
  echo "nginx rejected the new site, so it was switched off again. Your other sites are unchanged."
  exit 1
fi
systemctl reload nginx

echo "==> Getting the HTTPS certificate for $DOMAIN"
if ! certbot --nginx -d "$DOMAIN" --non-interactive --agree-tos --redirect --register-unsafely-without-email; then
  echo "certbot could not get a certificate. Check that $DOMAIN points to this server and ports 80/443 are open."
  echo "The game still works over http://$DOMAIN in the meantime."
  exit 1
fi

if curl -fsS "https://$DOMAIN/health" >/dev/null 2>&1; then
  echo
  echo "Big 2 is running. Open https://$DOMAIN"
else
  echo
  echo "Set up, but https://$DOMAIN/health did not answer from the server itself."
  echo "Try it in your browser; if it fails, see: sudo journalctl -u big2 -n 50 and sudo tail /var/log/nginx/error.log"
fi

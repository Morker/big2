#!/usr/bin/env bash
# Pulls the latest code from GitHub, builds it on the server, and restarts the game.
# Games in progress end when the server restarts.
#
# Usage, from the cloned repo: bash deploy/redeploy.sh
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO"

echo "==> Pulling the latest code"
git pull --ff-only

echo "==> Building (tests skipped; they run on your PC and in CI)"
mvn -q -B package -DskipTests
cp big2-server/target/big2-server.jar deploy/

echo "==> Restarting the game"
sudo bash deploy/update.sh

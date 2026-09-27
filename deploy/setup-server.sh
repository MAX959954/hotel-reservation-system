#!/usr/bin/env bash
# One-time preparation of a fresh Ubuntu 22.04/24.04 server (Hetzner, Oracle, ...).
# Run as root (or with sudo) on the server:
#   curl -fsSL https://raw.githubusercontent.com/<owner>/<repo>/main/deploy/setup-server.sh | sudo bash
# or, after cloning the repository:  sudo bash deploy/setup-server.sh
set -euo pipefail

echo "==> Updating packages"
apt-get update -y
DEBIAN_FRONTEND=noninteractive apt-get upgrade -y

echo "==> Installing Docker (official convenience script)"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker

echo "==> Automatic security updates"
DEBIAN_FRONTEND=noninteractive apt-get install -y unattended-upgrades git ufw
dpkg-reconfigure -f noninteractive unattended-upgrades

echo "==> Firewall: only SSH, HTTP, HTTPS"
ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable
# Note: Docker publishes ports through its own iptables rules, bypassing ufw. That's
# fine here because only Caddy (80/443) publishes ports in docker-compose.prod.yml.

echo "==> Swap (helps the Gradle/Vite builds on small servers)"
if ! swapon --show | grep -q .; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

if [ -n "${SUDO_USER:-}" ] && [ "$SUDO_USER" != "root" ]; then
  usermod -aG docker "$SUDO_USER"
  echo "==> Added $SUDO_USER to the docker group (log out and back in to use docker without sudo)"
fi

echo
echo "Done. Next: clone the repository, create deploy/.env, run deploy/deploy.sh (see deploy/README.md)."

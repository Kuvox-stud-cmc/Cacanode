#!/usr/bin/env bash
set -Eeuo pipefail

DEPLOY_USER="${1:-cacanode}"
SSH_PORT="${2:-22}"
DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/cacanode}"
SWAP_SIZE_GB="${SWAP_SIZE_GB:-4}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run this script as root: sudo $0 [deploy-user] [ssh-port]" >&2
  exit 1
fi

if ! [[ "${SSH_PORT}" =~ ^[0-9]+$ ]]; then
  echo "SSH port must be numeric" >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl gnupg ufw

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

# shellcheck disable=SC1091
. /etc/os-release
ARCH="$(dpkg --print-architecture)"
echo "deb [arch=${ARCH} signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

if [[ ! -e /etc/docker/daemon.json ]]; then
  printf '%s\n' \
    '{' \
    '  "log-driver": "json-file",' \
    '  "log-opts": {' \
    '    "max-size": "10m",' \
    '    "max-file": "3"' \
    '  }' \
    '}' > /etc/docker/daemon.json
else
  echo "Keeping existing /etc/docker/daemon.json; verify Docker log rotation manually."
fi
systemctl daemon-reload
systemctl enable docker
systemctl restart docker

if ! id "${DEPLOY_USER}" >/dev/null 2>&1; then
  useradd --create-home --shell /bin/bash "${DEPLOY_USER}"
fi
usermod -aG docker "${DEPLOY_USER}"

install -d -m 0750 -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" "${DEPLOY_ROOT}"
install -d -m 0750 -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" \
  "${DEPLOY_ROOT}/releases" "${DEPLOY_ROOT}/shared"

if [[ ! -f /swapfile ]]; then
  fallocate -l "${SWAP_SIZE_GB}G" /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
fi
if ! swapon --show=NAME --noheadings | grep -qx /swapfile; then
  swapon /swapfile
fi
if ! grep -q '^/swapfile ' /etc/fstab; then
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
sysctl vm.swappiness=10
if grep -q '^vm.swappiness=' /etc/sysctl.conf; then
  sed -i 's/^vm\.swappiness=.*/vm.swappiness=10/' /etc/sysctl.conf
else
  echo 'vm.swappiness=10' >> /etc/sysctl.conf
fi

ufw default deny incoming
ufw default allow outgoing
ufw allow "${SSH_PORT}/tcp"
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

echo "Bootstrap complete. Add an SSH public key for ${DEPLOY_USER}, then reconnect so Docker group membership takes effect."

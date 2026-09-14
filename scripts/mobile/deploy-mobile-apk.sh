#!/usr/bin/env bash
set -euo pipefail

ENVIRONMENT=${1:?usage: deploy-mobile-apk.sh <uat|prod> <apk> <sha256-file> <version>}
APK=${2:?APK path is required}
SHA_FILE=${3:?SHA-256 file path is required}
VERSION=${4:?version is required}

case "$ENVIRONMENT" in
  uat) CHANNEL="UAT" ;;
  prod) CHANNEL="PROD" ;;
  *) echo "environment must be uat or prod" >&2; exit 2 ;;
esac

[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
  echo "Invalid mobile version: $VERSION" >&2
  exit 2
}
[[ -s "$APK" ]] || { echo "APK missing/empty: $APK" >&2; exit 2; }
[[ -s "$SHA_FILE" ]] || { echo "Checksum file missing/empty: $SHA_FILE" >&2; exit 2; }

EXPECTED_SHA=$(awk 'NR==1 {print tolower($1)}' "$SHA_FILE")
[[ "$EXPECTED_SHA" =~ ^[0-9a-f]{64}$ ]] || {
  echo "Invalid SHA-256 file: $SHA_FILE" >&2
  exit 2
}
ACTUAL_SHA=$(sha256sum "$APK" | awk '{print tolower($1)}')
[[ "$ACTUAL_SHA" == "$EXPECTED_SHA" ]] || {
  echo "APK SHA-256 mismatch before server install: expected=$EXPECTED_SHA actual=$ACTUAL_SHA" >&2
  exit 1
}

DEST_DIR="/srv/dse-erp/${ENVIRONMENT}/mobile/android/${ENVIRONMENT}"
DEST_APK="${DEST_DIR}/Jasvi-Mobile-${VERSION}-${CHANNEL}.apk"
DEST_SHA="${DEST_APK}.sha256"
TMP_SHA=$(mktemp)
trap 'rm -f "$TMP_SHA"' EXIT
printf '%s  %s\n' "$ACTUAL_SHA" "$(basename "$DEST_APK")" > "$TMP_SHA"

# Keep mobile packages isolated underneath the existing UAT/PROD server roots.
# No database, server JAR, current symlink or systemd service is touched here.
sudo -n install -d -o dseerp -g dseerp -m 0755 "$DEST_DIR"
sudo -n install -o dseerp -g dseerp -m 0644 "$APK" "$DEST_APK"
sudo -n install -o dseerp -g dseerp -m 0644 "$TMP_SHA" "$DEST_SHA"

# The one-time environment bootstrap installs a persistent SELinux fcontext
# mapping for the mobile static directory. Newly installed files must receive it.
SELINUX_MODE="$(getenforce 2>/dev/null || echo Disabled)"
if [[ "$SELINUX_MODE" != "Disabled" ]]; then
  sudo -n restorecon -RF "$DEST_DIR"
  APK_LABEL="$(sudo -n ls -Z "$DEST_APK")"
  SHA_LABEL="$(sudo -n ls -Z "$DEST_SHA")"
  echo "SELinux APK label: $APK_LABEL"
  echo "SELinux checksum label: $SHA_LABEL"
  grep -q ':httpd_sys_content_t:' <<<"$APK_LABEL" || {
    echo "$CHANNEL APK is not labeled httpd_sys_content_t; run the ${CHANNEL} Caddy bootstrap first." >&2
    exit 1
  }
  grep -q ':httpd_sys_content_t:' <<<"$SHA_LABEL" || {
    echo "$CHANNEL checksum is not labeled httpd_sys_content_t; run the ${CHANNEL} Caddy bootstrap first." >&2
    exit 1
  }
fi

INSTALLED_SHA=$(sudo -n sha256sum "$DEST_APK" | awk '{print tolower($1)}')
[[ "$INSTALLED_SHA" == "$ACTUAL_SHA" ]] || {
  echo "Installed APK checksum mismatch: expected=$ACTUAL_SHA actual=$INSTALLED_SHA" >&2
  exit 1
}

sudo -n test -s "$DEST_SHA"

echo "MOBILE_APK_DEPLOYED environment=$ENVIRONMENT version=$VERSION"
echo "APK=$DEST_APK"
echo "SHA256=$INSTALLED_SHA"

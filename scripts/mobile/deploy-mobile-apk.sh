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
ENV_FILE="/etc/dse-erp/${ENVIRONMENT}.env"
SERVICE="dse-erp-${ENVIRONMENT}"
EXPECTED_ENV=$(printf '%s' "$ENVIRONMENT" | tr '[:lower:]' '[:upper:]')
TMP_SHA=$(mktemp)
POLICY_BACKUP=$(mktemp)
POLICY_TMP=$(mktemp)
trap 'rm -f "$TMP_SHA" "$POLICY_BACKUP" "$POLICY_TMP"' EXIT
printf '%s  %s\n' "$ACTUAL_SHA" "$(basename "$DEST_APK")" > "$TMP_SHA"

# Keep APK files isolated underneath the existing UAT/PROD server roots.
# The database, server JAR and current release symlink are never modified here.
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

# Publishing an APK and advertising it are one fail-closed operation. The ERP
# runtime owns the mobile compatibility policy, so update only the Android
# latest-version variable after the exact APK has been installed and verified.
# The minimum-supported version is deliberately left unchanged.
sudo -n test -r "$ENV_FILE" || {
  echo "Server environment file is not readable through deployment sudo: $ENV_FILE" >&2
  exit 1
}

SERVER_PORT=$(sudo -n awk -F= '
  /^[[:space:]]*(export[[:space:]]+)?DSE_SERVER_PORT=/ {
    v=$0; sub(/^[^=]*=/,"",v); gsub(/^[[:space:]"\047]+|[[:space:]"\047]+$/,"",v); print v; exit
  }
' "$ENV_FILE")
SERVER_PORT=${SERVER_PORT:-8081}
[[ "$SERVER_PORT" =~ ^[0-9]+$ ]] || { echo "Invalid DSE_SERVER_PORT in $ENV_FILE" >&2; exit 1; }
LOCAL_HEALTH_URL="http://127.0.0.1:${SERVER_PORT}/api/runtime/health"

PRE_BODY=$(curl --fail --silent --show-error --max-time 8 "$LOCAL_HEALTH_URL") || {
  echo "Current $CHANNEL ERP server is not healthy; refusing to advertise a new mobile version." >&2
  exit 1
}
PRE_STATE=$(python3 - "$EXPECTED_ENV" "$PRE_BODY" <<'PY'
import json,sys
env,body=sys.argv[1:]
r=json.loads(body)
if r.get('ready') is not True or str(r.get('environment','')).upper()!=env:
    raise SystemExit(f'Unexpected server health before mobile policy update: {r}')
version=str(r.get('version','')).strip()
minimum=str(r.get('minimumSupportedAndroidVersion','')).strip()
latest=str(r.get('latestAndroidVersion','')).strip()
if not version or not minimum:
    raise SystemExit(f'Incomplete Android compatibility policy before update: {r}')
print(f'{version}|{minimum}|{latest}')
PY
)
IFS='|' read -r SERVER_VERSION MINIMUM_ANDROID PREVIOUS_LATEST_ANDROID <<<"$PRE_STATE"

validate_policy_health() {
  local expected_latest=$1
  local body=$2
  python3 - "$EXPECTED_ENV" "$SERVER_VERSION" "$MINIMUM_ANDROID" "$expected_latest" "$body" <<'PY'
import json,sys
env,server_version,minimum,latest,body=sys.argv[1:]
r=json.loads(body)
ok=(r.get('ready') is True
    and str(r.get('environment','')).upper()==env
    and str(r.get('version','')).strip()==server_version
    and str(r.get('buildRevision','')).strip()==server_version
    and str(r.get('minimumSupportedAndroidVersion','')).strip()==minimum
    and str(r.get('latestAndroidVersion','')).strip()==latest)
raise SystemExit(0 if ok else 1)
PY
}

wait_for_policy() {
  local expected_latest=$1
  local body=''
  for attempt in $(seq 1 45); do
    if body=$(curl --fail --silent --show-error --max-time 3 "$LOCAL_HEALTH_URL" 2>/dev/null); then
      if validate_policy_health "$expected_latest" "$body"; then
        printf '%s' "$body"
        return 0
      fi
    fi
    echo "Waiting for $CHANNEL Android latest=$expected_latest ... attempt $attempt/45" >&2
    sleep 2
  done
  return 1
}

if [[ "$PREVIOUS_LATEST_ANDROID" != "$VERSION" ]]; then
  sudo -n cat "$ENV_FILE" > "$POLICY_BACKUP"
  awk -v version="$VERSION" '
    BEGIN { updated=0 }
    /^[[:space:]]*(export[[:space:]]+)?DSE_LATEST_ANDROID_VERSION=/ {
      print "DSE_LATEST_ANDROID_VERSION=" version
      updated=1
      next
    }
    { print }
    END { if (!updated) print "DSE_LATEST_ANDROID_VERSION=" version }
  ' "$POLICY_BACKUP" > "$POLICY_TMP"

  rollback_policy() {
    echo "Rolling Android latest-version policy back to ${PREVIOUS_LATEST_ANDROID:-previous server default}." >&2
    sudo -n cp "$POLICY_BACKUP" "$ENV_FILE"
    sudo -n systemctl restart "$SERVICE" || true
    if [[ -n "$PREVIOUS_LATEST_ANDROID" ]]; then
      wait_for_policy "$PREVIOUS_LATEST_ANDROID" >/dev/null || {
        echo "CRITICAL: $SERVICE did not recover its previous Android policy." >&2
        return 1
      }
    fi
  }

  sudo -n cp "$POLICY_TMP" "$ENV_FILE"
  if ! sudo -n systemctl restart "$SERVICE"; then
    rollback_policy
    exit 1
  fi
  if ! POLICY_BODY=$(wait_for_policy "$VERSION"); then
    sudo -n journalctl -u "$SERVICE" -n 80 --no-pager >&2 || true
    rollback_policy
    exit 1
  fi
  sudo -n systemctl is-active --quiet "$SERVICE" || {
    rollback_policy
    exit 1
  }
  echo "$POLICY_BODY"
else
  validate_policy_health "$VERSION" "$PRE_BODY" || {
    echo "Existing Android latest-version policy failed validation." >&2
    exit 1
  }
fi

echo "MOBILE_APK_DEPLOYED environment=$ENVIRONMENT version=$VERSION"
echo "MOBILE_ANDROID_POLICY_OK environment=$ENVIRONMENT minimum=$MINIMUM_ANDROID latest=$VERSION server=$SERVER_VERSION"
echo "APK=$DEST_APK"
echo "SHA256=$INSTALLED_SHA"

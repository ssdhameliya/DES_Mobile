#!/usr/bin/env bash
set -euo pipefail

HOST=${1:-api.jasviindustries.in}
CONFIG_FILE=/etc/caddy/Caddyfile
STATIC_DIR=/srv/dse-erp/prod/mobile/android/prod
SELINUX_PATTERN="${STATIC_DIR}(/.*)?"
MARKER="# DSE_MOBILE_PROD_STATIC_BEGIN"
HEALTH_URL="https://${HOST}/api/runtime/health"
PROBE_NAME="mobile-route-bootstrap-probe.txt"
PROBE_URL="https://${HOST}/mobile/android/prod/${PROBE_NAME}"

# PROD-only bootstrap. Refuse any other hostname.
[[ "$HOST" == "api.jasviindustries.in" ]] || {
  echo "Refusing to modify any host except api.jasviindustries.in: $HOST" >&2
  exit 2
}

command -v caddy >/dev/null 2>&1 || { echo "Caddy is not installed." >&2; exit 1; }
sudo -n systemctl is-active --quiet caddy || { echo "Caddy service is not active." >&2; exit 1; }
sudo -n test -f "$CONFIG_FILE" || { echo "Missing Caddyfile: $CONFIG_FILE" >&2; exit 1; }

# Keep APK files isolated under the existing DSE PROD filesystem root.
sudo -n install -d -o dseerp -g dseerp -m 0755 "$STATIC_DIR"

# POSIX/DAC access must remain readable/traversable by the Caddy service account.
sudo -n -u caddy test -x "$STATIC_DIR" || {
  echo "Caddy user cannot traverse $STATIC_DIR" >&2
  exit 1
}
sudo -n -u caddy test -r "$STATIC_DIR" || {
  echo "Caddy user cannot read $STATIC_DIR" >&2
  exit 1
}

CURRENT=$(mktemp)
UPDATED=$(mktemp)
PROBE_LOCAL=$(mktemp)
BACKUP=""
CHANGED=false
SELINUX_RULE_ADDED=false
cleanup() {
  rm -f "$CURRENT" "$UPDATED" "$PROBE_LOCAL"
  sudo -n rm -f "$STATIC_DIR/$PROBE_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT
sudo -n cat "$CONFIG_FILE" > "$CURRENT"

rollback_selinux() {
  if [[ "$SELINUX_RULE_ADDED" == "true" ]]; then
    echo "Removing SELinux file-context rule added by this failed bootstrap." >&2
    sudo -n semanage fcontext -d "$SELINUX_PATTERN" || true
    sudo -n restorecon -RF "$STATIC_DIR" || true
  fi
}

rollback_caddy() {
  if [[ "$CHANGED" == "true" && -n "$BACKUP" ]]; then
    echo "Restoring previous Caddy configuration from $BACKUP" >&2
    sudo -n cp -a "$BACKUP" "$CONFIG_FILE"
    sudo -n caddy validate --config "$CONFIG_FILE" --adapter caddyfile || true
    sudo -n systemctl reload caddy || true
  fi
}

rollback() {
  rollback_caddy
  rollback_selinux
}

# Oracle Linux runs Caddy in the SELinux httpd_t domain. Keep a narrow,
# persistent read-only web-content mapping for only the PROD mobile directory.
SELINUX_MODE="$(getenforce 2>/dev/null || echo Disabled)"
if [[ "$SELINUX_MODE" != "Disabled" ]]; then
  SEMANAGE="$(sudo -n sh -c 'command -v semanage' 2>/dev/null || true)"
  [[ -n "$SEMANAGE" ]] || {
    echo "SELinux is $SELINUX_MODE but semanage is unavailable. Install policycoreutils-python-utils before retrying; SELinux will not be disabled or weakened." >&2
    exit 1
  }

  EXISTING_RULE="$(sudo -n semanage fcontext -l | grep -F "$SELINUX_PATTERN" || true)"
  if [[ -n "$EXISTING_RULE" ]]; then
    if ! grep -q 'httpd_sys_content_t' <<<"$EXISTING_RULE"; then
      echo "Conflicting SELinux fcontext rule already exists for $SELINUX_PATTERN:" >&2
      echo "$EXISTING_RULE" >&2
      echo "Refusing to overwrite an existing SELinux policy mapping." >&2
      exit 1
    fi
    echo "SELinux web-content mapping already exists for $SELINUX_PATTERN"
  else
    sudo -n semanage fcontext -a -t httpd_sys_content_t "$SELINUX_PATTERN"
    SELINUX_RULE_ADDED=true
    echo "Added persistent SELinux mapping: $SELINUX_PATTERN -> httpd_sys_content_t"
  fi

  sudo -n restorecon -RF "$STATIC_DIR"
  LABEL="$(sudo -n ls -Zd "$STATIC_DIR")"
  echo "SELinux static directory label: $LABEL"
  grep -q ':httpd_sys_content_t:' <<<"$LABEL" || {
    echo "Static directory did not receive httpd_sys_content_t." >&2
    rollback_selinux
    exit 1
  }
fi

# Guard against editing the wrong reverse proxy or an unexpected Caddy layout.
# We do not assume a production backend port; instead require exactly one
# reverse_proxy line inside the exact production site block and insert before it.
python3 - "$HOST" "$CURRENT" <<'PY'
import re,sys
host,path=sys.argv[1:]
lines=open(path,encoding='utf-8').read().splitlines(True)
start=None
depth=0
site=None
for i,line in enumerate(lines):
    if start is None:
        if re.match(r'^\s*'+re.escape(host)+r'\s*\{\s*$', line):
            start=i
            depth=line.count('{')-line.count('}')
    else:
        depth += line.count('{')-line.count('}')
        if depth == 0:
            site=(start,i)
            break
if site is None:
    raise SystemExit(f'Unable to isolate Caddy site block for {host}')
start,end=site
proxies=[i for i in range(start+1,end) if re.match(r'^\s*reverse_proxy\s+\S+', lines[i])]
if len(proxies) != 1:
    raise SystemExit(f'Expected exactly one production reverse_proxy line, found {len(proxies)}')
print('PROD_CADDY_SITE_GUARD_OK')
PY

if grep -Fq "$MARKER" "$CURRENT"; then
  echo "PROD Caddy mobile static route is already configured."
  sudo -n caddy validate --config "$CONFIG_FILE" --adapter caddyfile
else
  python3 - "$HOST" "$CURRENT" "$UPDATED" <<'PY'
import re,sys
host,src,dst=sys.argv[1:]
lines=open(src,encoding='utf-8').read().splitlines(True)
start=None
depth=0
site=None
for i,line in enumerate(lines):
    if start is None:
        if re.match(r'^\s*'+re.escape(host)+r'\s*\{\s*$', line):
            start=i
            depth=line.count('{')-line.count('}')
    else:
        depth += line.count('{')-line.count('}')
        if depth == 0:
            site=(start,i)
            break
if site is None:
    raise SystemExit(f'Unable to isolate Caddy site block for {host}')
start,end=site
proxy_indexes=[i for i in range(start+1,end) if re.match(r'^\s*reverse_proxy\s+\S+', lines[i])]
if len(proxy_indexes) != 1:
    raise SystemExit(f'Expected exactly one production reverse_proxy line, found {len(proxy_indexes)}')
proxy_i=proxy_indexes[0]
indent=re.match(r'^(\s*)', lines[proxy_i]).group(1)
inner=indent+'    '
block=(
    f"{indent}# DSE_MOBILE_PROD_STATIC_BEGIN\n"
    f"{indent}handle_path /mobile/android/prod/* {{\n"
    f"{inner}root * /srv/dse-erp/prod/mobile/android/prod\n"
    f"{inner}header Cache-Control \"no-store\"\n"
    f"{inner}header X-Content-Type-Options \"nosniff\"\n"
    f"{inner}file_server\n"
    f"{indent}}}\n"
    f"{indent}# DSE_MOBILE_PROD_STATIC_END\n\n"
)
lines.insert(proxy_i, block)
open(dst,'w',encoding='utf-8',newline='').writelines(lines)
PY

  STAMP=$(date -u +%Y%m%dT%H%M%SZ)
  BACKUP="${CONFIG_FILE}.pre-dse-mobile-prod-${STAMP}"
  sudo -n cp -a "$CONFIG_FILE" "$BACKUP"
  sudo -n cp "$UPDATED" "$CONFIG_FILE"
  CHANGED=true
fi

if ! sudo -n caddy validate --config "$CONFIG_FILE" --adapter caddyfile; then
  echo "Caddy configuration validation failed." >&2
  rollback
  exit 1
fi

if [[ "$CHANGED" == "true" ]]; then
  if ! sudo -n systemctl reload caddy; then
    echo "Caddy reload failed." >&2
    rollback
    exit 1
  fi
fi

HEALTH_BODY=$(curl --fail --silent --show-error --max-time 20 "$HEALTH_URL") || {
  echo "Public PROD health request failed after Caddy configuration." >&2
  rollback
  exit 1
}
if ! python3 - "$HEALTH_BODY" <<'PY'
import json,sys
r=json.loads(sys.argv[1])
env=str(r.get('environment','')).upper()
if r.get('ready') is not True or env not in {'PROD','PRODUCTION'}:
    raise SystemExit(f'Unexpected PROD health after Caddy configuration: {r}')
print('PUBLIC_PROD_HEALTH_OK')
PY
then
  rollback
  exit 1
fi

# Prove the route with a file carrying the persistent SELinux mapping.
printf 'DSE_MOBILE_PROD_CADDY_ROUTE_OK\n' > "$PROBE_LOCAL"
sudo -n install -o dseerp -g dseerp -m 0644 "$PROBE_LOCAL" "$STATIC_DIR/$PROBE_NAME"
if [[ "$SELINUX_MODE" != "Disabled" ]]; then
  sudo -n restorecon -F "$STATIC_DIR/$PROBE_NAME"
  PROBE_LABEL="$(sudo -n ls -Z "$STATIC_DIR/$PROBE_NAME")"
  echo "SELinux probe label: $PROBE_LABEL"
  grep -q ':httpd_sys_content_t:' <<<"$PROBE_LABEL" || {
    echo "Probe file did not receive httpd_sys_content_t." >&2
    rollback
    exit 1
  }
fi

PROBE_BODY=$(curl --fail --silent --show-error --max-time 20 "$PROBE_URL") || {
  echo "Public PROD mobile static route probe failed." >&2
  rollback
  exit 1
}
if [[ "$PROBE_BODY" != "DSE_MOBILE_PROD_CADDY_ROUTE_OK" ]]; then
  echo "Unexpected public PROD mobile route probe response: $PROBE_BODY" >&2
  rollback
  exit 1
fi

echo "MOBILE_PROD_CADDY_BOOTSTRAP_OK"
echo "Host: $HOST"
echo "URL prefix: /mobile/android/prod/"
echo "Filesystem: $STATIC_DIR"
echo "SELinux: $SELINUX_MODE"
if [[ -n "$BACKUP" ]]; then
  echo "Backup: $BACKUP"
else
  echo "Backup: existing route reused; no config change required"
fi

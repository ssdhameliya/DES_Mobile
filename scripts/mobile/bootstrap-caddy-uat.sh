#!/usr/bin/env bash
set -euo pipefail

HOST=${1:-api-uat.jasviindustries.in}
CONFIG_FILE=/etc/caddy/Caddyfile
STATIC_DIR=/srv/dse-erp/uat/mobile/android/uat
MARKER="# DSE_MOBILE_UAT_STATIC_BEGIN"
HEALTH_URL="https://${HOST}/api/runtime/health"

# UAT-only bootstrap. Production is intentionally not changed here.
[[ "$HOST" == "api-uat.jasviindustries.in" ]] || {
  echo "Refusing to modify any host except api-uat.jasviindustries.in: $HOST" >&2
  exit 2
}

command -v caddy >/dev/null 2>&1 || { echo "Caddy is not installed." >&2; exit 1; }
sudo -n systemctl is-active --quiet caddy || { echo "Caddy service is not active." >&2; exit 1; }
sudo -n test -f "$CONFIG_FILE" || { echo "Missing Caddyfile: $CONFIG_FILE" >&2; exit 1; }

# Keep APK files isolated under the existing DSE UAT filesystem root.
sudo -n install -d -o dseerp -g dseerp -m 0755 "$STATIC_DIR"

# Refuse to continue if the Caddy service account cannot traverse/read the directory.
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
cleanup() { rm -f "$CURRENT" "$UPDATED"; }
trap cleanup EXIT
sudo -n cat "$CONFIG_FILE" > "$CURRENT"

# Guard against editing the wrong reverse proxy or an unexpected Caddy layout.
python3 - "$HOST" "$CURRENT" <<'PY'
import re,sys
host,path=sys.argv[1:]
text=open(path,encoding='utf-8').read()
if host not in text:
    raise SystemExit(f'UAT host {host} is not present in Caddyfile')
if not re.search(r'(?m)^\s*reverse_proxy\s+127\.0\.0\.1:8081\s*$', text):
    raise SystemExit('Expected UAT reverse_proxy 127.0.0.1:8081 was not found')
PY

if grep -Fq "$MARKER" "$CURRENT"; then
  echo "UAT Caddy mobile static route is already configured."
  sudo -n caddy validate --config "$CONFIG_FILE" --adapter caddyfile
  exit 0
fi

python3 - "$HOST" "$CURRENT" "$UPDATED" <<'PY'
import re,sys
host,src,dst=sys.argv[1:]
lines=open(src,encoding='utf-8').read().splitlines(True)

# Find the top-level site block for the exact UAT hostname.
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
proxy_indexes=[]
for i in range(start+1,end):
    if re.match(r'^\s*reverse_proxy\s+127\.0\.0\.1:8081\s*$', lines[i]):
        proxy_indexes.append(i)
if len(proxy_indexes) != 1:
    raise SystemExit(f'Expected exactly one UAT reverse_proxy line, found {len(proxy_indexes)}')

proxy_i=proxy_indexes[0]
indent=re.match(r'^(\s*)', lines[proxy_i]).group(1)
inner=indent+'    '
block=(
    f"{indent}# DSE_MOBILE_UAT_STATIC_BEGIN\n"
    f"{indent}handle_path /mobile/android/uat/* {{\n"
    f"{inner}root * /srv/dse-erp/uat/mobile/android/uat\n"
    f"{inner}header Cache-Control \"no-store\"\n"
    f"{inner}header X-Content-Type-Options \"nosniff\"\n"
    f"{inner}file_server\n"
    f"{indent}}}\n"
    f"{indent}# DSE_MOBILE_UAT_STATIC_END\n\n"
)
lines.insert(proxy_i, block)
open(dst,'w',encoding='utf-8',newline='').writelines(lines)
PY

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
BACKUP="${CONFIG_FILE}.pre-dse-mobile-${STAMP}"
sudo -n cp -a "$CONFIG_FILE" "$BACKUP"
sudo -n cp "$UPDATED" "$CONFIG_FILE"

rollback() {
  echo "Restoring previous Caddy configuration from $BACKUP" >&2
  sudo -n cp -a "$BACKUP" "$CONFIG_FILE"
  sudo -n caddy validate --config "$CONFIG_FILE" --adapter caddyfile || true
  sudo -n systemctl reload caddy || true
}

if ! sudo -n caddy validate --config "$CONFIG_FILE" --adapter caddyfile; then
  echo "New Caddy configuration failed validation." >&2
  rollback
  exit 1
fi

if ! sudo -n systemctl reload caddy; then
  echo "Caddy reload failed." >&2
  rollback
  exit 1
fi

# Ensure the existing public UAT API still works after the proxy reload.
HEALTH_BODY=$(curl --fail --silent --show-error --max-time 20 "$HEALTH_URL") || {
  echo "Public UAT health request failed after Caddy reload." >&2
  rollback
  exit 1
}
python3 - "$HEALTH_BODY" <<'PY'
import json,sys
r=json.loads(sys.argv[1])
if r.get('ready') is not True or r.get('environment') != 'UAT':
    raise SystemExit(f'Unexpected UAT health after Caddy reload: {r}')
print('PUBLIC_UAT_HEALTH_OK')
PY

echo "MOBILE_UAT_CADDY_BOOTSTRAP_OK"
echo "Host: $HOST"
echo "URL prefix: /mobile/android/uat/"
echo "Filesystem: $STATIC_DIR"
echo "Backup: $BACKUP"

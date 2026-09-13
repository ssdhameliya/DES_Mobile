#!/usr/bin/env bash
set -euo pipefail

HOST=${1:-api-uat.jasviindustries.in}
URL_PREFIX="/mobile/android/uat/"
ALIAS_DIR="/srv/dse-erp/uat/mobile/android/uat/"
MARKER="# DSE_MOBILE_UAT_STATIC_BEGIN"

# This is intentionally UAT-only. Production gets a separate reviewed bootstrap
# after the UAT update path has been proven end-to-end.
sudo -n install -d -o dseerp -g dseerp -m 0755 "$ALIAS_DIR"

DUMP=$(mktemp)
CURRENT=$(mktemp)
UPDATED=$(mktemp)
cleanup() { rm -f "$DUMP" "$CURRENT" "$UPDATED"; }
trap cleanup EXIT

sudo -n nginx -T >"$DUMP" 2>&1 || {
  cat "$DUMP" >&2
  echo "Unable to inspect active Nginx configuration with sudo -n nginx -T." >&2
  exit 1
}

CONFIG_FILE=$(python3 - "$HOST" "$DUMP" <<'PY'
import re,sys
host,path=sys.argv[1:]
text=open(path,encoding='utf-8',errors='replace').read().splitlines()
segments=[]
current_path=None
buf=[]
for line in text:
    m=re.match(r'^# configuration file (.+):$', line)
    if m:
        if current_path is not None:
            segments.append((current_path,'\n'.join(buf)))
        current_path=m.group(1)
        buf=[]
    elif current_path is not None:
        buf.append(line)
if current_path is not None:
    segments.append((current_path,'\n'.join(buf)))

for file_path,body in segments:
    if host not in body:
        continue
    if not re.search(r'\blisten\s+[^;]*\b443\b[^;]*;', body):
        continue
    if re.search(r'\bserver_name\s+[^;]*\b'+re.escape(host)+r'\b[^;]*;', body):
        print(file_path)
        raise SystemExit(0)
raise SystemExit(1)
PY
) || {
  echo "Could not locate the active HTTPS Nginx server block for $HOST." >&2
  echo "No Nginx file was changed." >&2
  exit 1
}

CONFIG_FILE=$(sudo -n readlink -f "$CONFIG_FILE")
[[ -n "$CONFIG_FILE" ]] || { echo "Unable to resolve Nginx config path." >&2; exit 1; }
echo "UAT Nginx config: $CONFIG_FILE"

sudo -n cat "$CONFIG_FILE" > "$CURRENT"

if grep -Fq "$MARKER" "$CURRENT"; then
  echo "UAT mobile static route is already configured."
  sudo -n nginx -t
  exit 0
fi

python3 - "$HOST" "$CURRENT" "$UPDATED" <<'PY'
import re,sys
host,src,dst=sys.argv[1:]
lines=open(src,encoding='utf-8').read().splitlines(True)
start=None
depth=0
candidate=None
for i,line in enumerate(lines):
    if start is None:
        if re.match(r'^\s*server\s*\{', line):
            start=i
            depth=line.count('{')-line.count('}')
    else:
        depth += line.count('{')-line.count('}')
        if depth == 0:
            block=''.join(lines[start:i+1])
            if (re.search(r'\bserver_name\s+[^;]*\b'+re.escape(host)+r'\b[^;]*;', block)
                    and re.search(r'\blisten\s+[^;]*\b443\b[^;]*;', block)):
                candidate=(start,i)
                break
            start=None

if candidate is None:
    raise SystemExit(f'Unable to isolate HTTPS server block for {host}')

_,end=candidate
indent=re.match(r'^(\s*)', lines[end]).group(1) + '    '
block=(
    f"{indent}# DSE_MOBILE_UAT_STATIC_BEGIN\n"
    f"{indent}location ^~ /mobile/android/uat/ {{\n"
    f"{indent}    alias /srv/dse-erp/uat/mobile/android/uat/;\n"
    f"{indent}    types {{\n"
    f"{indent}        application/vnd.android.package-archive apk;\n"
    f"{indent}        text/plain sha256;\n"
    f"{indent}    }}\n"
    f"{indent}    default_type application/octet-stream;\n"
    f"{indent}    add_header Cache-Control \"no-store\" always;\n"
    f"{indent}    add_header X-Content-Type-Options \"nosniff\" always;\n"
    f"{indent}}}\n"
    f"{indent}# DSE_MOBILE_UAT_STATIC_END\n"
)
lines.insert(end, block)
open(dst,'w',encoding='utf-8',newline='').writelines(lines)
PY

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
BACKUP="${CONFIG_FILE}.pre-dse-mobile-${STAMP}"
sudo -n cp -a "$CONFIG_FILE" "$BACKUP"
sudo -n cp "$UPDATED" "$CONFIG_FILE"

if ! sudo -n nginx -t; then
  echo "New Nginx configuration failed validation; restoring $BACKUP" >&2
  sudo -n cp -a "$BACKUP" "$CONFIG_FILE"
  sudo -n nginx -t || true
  exit 1
fi

if ! sudo -n systemctl reload nginx; then
  echo "Nginx reload failed; restoring previous configuration." >&2
  sudo -n cp -a "$BACKUP" "$CONFIG_FILE"
  sudo -n nginx -t || true
  sudo -n systemctl reload nginx || true
  exit 1
fi

echo "MOBILE_UAT_NGINX_BOOTSTRAP_OK"
echo "Host: $HOST"
echo "URL prefix: $URL_PREFIX"
echo "Filesystem: $ALIAS_DIR"
echo "Backup: $BACKUP"

from pathlib import Path

path = Path('mobile-source/DSE-ERP-Mobile/shared/build.gradle.kts')
lines = path.read_text().splitlines()
out = []
i = 0
while i < len(lines):
    line = lines[i]
    stripped = line.strip()
    if stripped.startswith('val isMacOs ='):
        i += 1
        if i < len(lines) and '.startsWith(' in lines[i]:
            i += 1
        continue
    if stripped.startswith('if (isMacOs) {'):
        depth = line.count('{') - line.count('}')
        i += 1
        while i < len(lines) and depth > 0:
            depth += lines[i].count('{') - lines[i].count('}')
            i += 1
        continue
    # Defensive removal if a previous transform left a direct native target/client line.
    if any(token in line for token in ('iosArm64()', 'iosSimulatorArm64()', 'ktor-client-darwin', 'KotlinNativeTarget')):
        i += 1
        continue
    out.append(line)
    i += 1

text = '\n'.join(out).rstrip() + '\n'
for forbidden in ('isMacOs', 'iosArm64()', 'iosSimulatorArm64()', 'ktor-client-darwin', 'KotlinNativeTarget'):
    if forbidden in text:
        raise SystemExit(f'Native iOS residue still present in shared/build.gradle.kts: {forbidden}')
path.write_text(text)
print('SHARED_GRADLE_ANDROID_ONLY_OK')

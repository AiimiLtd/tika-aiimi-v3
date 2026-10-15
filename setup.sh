#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WRAPPER_PROPS="${SCRIPT_DIR}/.mvn/wrapper/maven-wrapper.properties"
MARKER_BEGIN="# >>> aiimi-maven >>>"
MARKER_END="# <<< aiimi-maven <<<"

sudo apt-get install -y openjdk-21-jdk-headless openjdk-21-jre-headless wget unzip
# Drop the distro Maven package so /usr/bin/mvn (often 3.6.x) cannot win.
sudo apt-get remove -y maven || true

distributionUrl="$(
  grep -E '^distributionUrl=' "${WRAPPER_PROPS}" \
    | cut -d= -f2- \
    | tr -d '\r' \
    | sed 's|\\:|:|g'
)"
maven="$(printf '%s\n' "${distributionUrl}" | sed -n 's|.*/apache-maven/\([^/]*\)/.*|\1|p')"

if [ -z "${maven}" ] || [ -z "${distributionUrl}" ]; then
  echo "Could not derive Maven version from ${WRAPPER_PROPS}" >&2
  exit 1
fi

echo "Installing Maven ${maven} (from .mvn/wrapper/maven-wrapper.properties)"

maven_root="${HOME}/.maven"
maven_home="${maven_root}/apache-maven-${maven}"
mkdir -p "${maven_root}"

if [ ! -x "${maven_home}/bin/mvn" ]; then
  tmp="$(mktemp -d)"
  archive="${tmp}/$(basename "${distributionUrl}")"
  wget -O "${archive}" "${distributionUrl}"
  case "${archive}" in
    *.zip) unzip -q "${archive}" -d "${tmp}" ;;
    *.tar.gz|*.tgz) tar -xzf "${archive}" -C "${tmp}" ;;
    *) echo "Unsupported Maven archive: ${archive}" >&2; exit 1 ;;
  esac
  extracted="$(find "${tmp}" -maxdepth 1 -type d -name "apache-maven-${maven}" | head -1)"
  rm -rf "${maven_home}"
  mv "${extracted}" "${maven_home}"
  rm -rf "${tmp}"
fi

ln -sfn "${maven_home}" "${maven_root}/current"

# Replace any previous Aiimi Maven PATH block so older versions do not stay ahead.
profile="${HOME}/.profile"
touch "${profile}"
tmp_profile="$(mktemp)"
awk -v begin="${MARKER_BEGIN}" -v end="${MARKER_END}" '
  $0 == begin {skip=1; next}
  $0 == end {skip=0; next}
  !skip {print}
' "${profile}" > "${tmp_profile}"
cat >> "${tmp_profile}" <<EOF
${MARKER_BEGIN}
export PATH="${maven_root}/current/bin:\$PATH"
${MARKER_END}
EOF
mv "${tmp_profile}" "${profile}"

export PATH="${maven_root}/current/bin:${PATH}"
hash -r 2>/dev/null || true

echo "Maven on PATH: $(command -v mvn)"
mvn -version

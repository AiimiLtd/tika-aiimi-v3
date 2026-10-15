#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WRAPPER_PROPS="${SCRIPT_DIR}/.mvn/wrapper/maven-wrapper.properties"

distributionUrl="$(
  grep -E '^distributionUrl=' "${WRAPPER_PROPS}" \
    | cut -d= -f2- \
    | tr -d '\r' \
    | sed 's|\\:|:|g'
)"
maven="$(printf '%s\n' "${distributionUrl}" | sed -n 's|.*/apache-maven/\([^/]*\)/.*|\1|p')"

if [ -z "${maven}" ]; then
  echo "Could not derive Maven version from ${WRAPPER_PROPS}" >&2
  exit 1
fi

MVN="${HOME}/.maven/apache-maven-${maven}/bin/mvn"
if [ ! -x "${MVN}" ]; then
  MVN="${HOME}/.maven/current/bin/mvn"
fi
if [ ! -x "${MVN}" ]; then
  echo "Maven ${maven} is not installed. Run ./setup.sh first." >&2
  exit 1
fi

echo "Using $(${MVN} -version | head -1)"
"${MVN}" clean install -DskipTests=true -Dcheckstyle.skip -Dossindex.skip -e

target_dir="${SCRIPT_DIR}/tika-server/tika-server-standard/target"
staged="${target_dir}/tika-server-standard.jar"
matches=()
for jar in "${target_dir}"/tika-server-standard-*.jar; do
  [ -e "${jar}" ] || continue
  base="$(basename "${jar}")"
  case "${base}" in
    *-sources.jar|*-javadoc.jar|*-tests.jar|original-*|tika-server-standard.jar) continue ;;
  esac
  matches+=("${jar}")
done
if [ "${#matches[@]}" -ne 1 ]; then
  echo "Expected exactly one tika-server-standard jar, found: ${matches[*]:-none}" >&2
  exit 1
fi
cp "${matches[0]}" "${staged}"
echo "Staged ${matches[0]} -> ${staged}"

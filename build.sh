#!/bin/bash
set -e

ROOT=$(dirname "${BASH_SOURCE[0]}")

# Derive GAMA p2 version from branch name: GAMA_YYYY-MM → YYYY.MM
BRANCH="${GITHUB_REF_NAME:-$(git rev-parse --abbrev-ref HEAD)}"
if [[ "$BRANCH" =~ GAMA_([0-9]{4}-[0-9]{2}) ]]; then
    GAMA_P2_VERSION="${BASH_REMATCH[1]//-/.}"   # 2025-06 → 2025.06
    echo "Branch ${BRANCH} → gama.p2.version=${GAMA_P2_VERSION}"
else
    echo "ERROR: branch '${BRANCH}' does not match GAMA_YYYY-MM"
    exit 1
fi

cd "${ROOT}/parent"
mvn clean install -B \
    -Dgama.p2.version="${GAMA_P2_VERSION}" \
    -Ddeploy.subdir="${PLUGIN_REPO_NAME}" \
    -Dtycho.p2.transport.min-cache-minutes=0 \
    -Dtycho.equinox.resolver.uses=true \
    -P p2Repo \
    --settings ../settings.xml

#!/usr/bin/env bash
# Minecraft 1.21.8 variant, built from the same sources (only when a release asks for it).
#   ./build-1.21.8.sh          -> build/libs/Paletale-<version>-1.21.8.jar
#   ./build-1.21.8.sh test     -> also run the client smoke test on a real 1.21.8 client (needs a display)
set -euo pipefail
cd "$(dirname "$0")"
flags=(-Pmc_compat=1.21.8 -Pminecraft_version=1.21.8 -Pfabric_api_version=0.136.1+1.21.8)
./gradlew "${flags[@]}" build
if [[ "${1:-}" == test ]]; then ./gradlew "${flags[@]}" runClientGameTest -ParchiveOnly; fi

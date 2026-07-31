#!/usr/bin/env bash
#
# Run the CI checks locally before pushing.
#
# By default this prefers `act` (full GitHub Actions emulation via Docker, see
# .actrc). If act or Docker is unavailable, or you pass --gradle, it falls back to
# running the exact same Gradle tasks the CI job runs — faster and needs no Docker.
#
# Usage:
#   scripts/ci-local.sh            # act if available, else Gradle
#   scripts/ci-local.sh --gradle   # force the direct Gradle path
#   scripts/ci-local.sh --act      # force act (fails if unavailable)
#
set -euo pipefail

cd "$(dirname "$0")/.."

# The task list here MUST mirror .github/workflows/ci.yml.
GRADLE_TASKS=(lint testDebugUnitTest assembleDebug)

mode="auto"
case "${1:-}" in
  --gradle) mode="gradle"; shift ;;
  --act)    mode="act";    shift ;;
esac

run_gradle() {
  echo "==> Running CI tasks via Gradle: ${GRADLE_TASKS[*]}"
  ./gradlew "${GRADLE_TASKS[@]}" "$@"
}

run_act() {
  echo "==> Running CI via act (job: build)"
  act -j build "$@"
}

case "$mode" in
  gradle)
    run_gradle "$@"
    ;;
  act)
    run_act "$@"
    ;;
  auto)
    if command -v act >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
      run_act "$@"
    else
      echo "==> act or Docker not available; using Gradle directly."
      run_gradle "$@"
    fi
    ;;
esac

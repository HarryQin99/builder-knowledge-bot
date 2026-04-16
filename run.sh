#!/usr/bin/env bash
# Load .env and start the Spring Boot app.
# Usage: ./run.sh
set -euo pipefail

if [[ ! -f .env ]]; then
  echo "error: .env not found. Copy .env.example to .env and fill in your key." >&2
  exit 1
fi

# Export all vars defined in .env
set -a
# shellcheck disable=SC1091
source .env
set +a

exec ./gradlew bootRun

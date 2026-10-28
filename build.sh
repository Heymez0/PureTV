#!/usr/bin/env bash
# PureTV v1.1 portable build entry point.
set -euo pipefail
cd "$(dirname "$0")"
exec python3 build.py "$@"

#!/data/data/com.termux/files/usr/bin/bash
# Start AccessDroid Live View HTTP bridge
# Usage: ./live-view-server.sh [port]
set -euo pipefail
PORT="${1:-8765}"
cd "$(dirname "$0")"
python3 live-view-server.py "$PORT"

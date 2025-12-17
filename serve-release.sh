#!/bin/bash
set -e

PORT=5500
DIR="app/build/outputs/apk/release"

cd "$DIR"

# Pick APK: prefer app-release.apk, else first *.apk
APK_FILE="app-release.apk"
if [[ ! -f "$APK_FILE" ]]; then
  APK_FILE="$(ls -1 *.apk 2>/dev/null | head -1 || true)"
fi

if [[ -z "$APK_FILE" || ! -f "$APK_FILE" ]]; then
  echo "Error: No APK found in $(pwd)"
  exit 1
fi

# Get local Wi-Fi/LAN IP (macOS first, then Linux fallbacks)
get_lan_ip() {
  local ip
  ip="$(ipconfig getifaddr en0 2>/dev/null || true)"
  [[ -n "$ip" ]] && { echo "$ip"; return; }

  ip="$(ipconfig getifaddr en1 2>/dev/null || true)"
  [[ -n "$ip" ]] && { echo "$ip"; return; }

  ip="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
  [[ -n "$ip" ]] && { echo "$ip"; return; }

  ip="$(ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if ($i=="src") {print $(i+1); exit}}' || true)"
  [[ -n "$ip" ]] && { echo "$ip"; return; }

  echo ""
}

IP="$(get_lan_ip)"
URL=""

echo "Serving: $(pwd)"
echo "APK: $APK_FILE"

if [[ -n "$IP" ]]; then
  # URL-encode spaces minimally (just in case)
  APK_URL_PATH="${APK_FILE// /%20}"
  URL="http://$IP:$PORT/$APK_URL_PATH"
  echo "Direct APK URL: $URL"
else
  echo "Direct APK URL: http://<your-lan-ip>:$PORT/$APK_FILE   (couldn’t auto-detect IP)"
fi

# Print a QR code in terminal if qrencode is available
if [[ -n "$URL" ]]; then
  if command -v qrencode >/dev/null 2>&1; then
    echo ""
    echo "QR (scan to download APK):"
    qrencode -t ANSIUTF8 "$URL"
  else
    echo ""
    echo "Tip: install qrencode for terminal QR codes:"
    echo "  macOS: brew install qrencode"
    echo "  Ubuntu/Debian: sudo apt-get install qrencode"
  fi
fi

echo ""
echo "Starting HTTP server on :$PORT (Ctrl+C to stop)"
echo ""

# Start server (python3 preferred)
if command -v python3 >/dev/null 2>&1; then
  exec python3 -m http.server "$PORT"
else
  exec python -m http.server "$PORT"
fi

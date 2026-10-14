#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
G='\033[0;32m'; Y='\033[1;33m'; R='\033[0;31m'; N='\033[0m'
log() { echo -e "${G}[+]${N} $*"; }
warn() { echo -e "${Y}[!]${N} $*"; }
err() { echo -e "${R}[x]${N} $*"; }

APK="$HOME/bin/accessdroid/build/AccessDroid.apk"
[[ ! -f "$APK" ]] && { err "APK not found at $APK — run ./build.sh first"; exit 1; }

echo -e "${G}═══════════════════════════════════════════════════${N}"
echo -e "${G}  AccessDroid + Pi Agent — Full Setup              ${N}"
echo -e "${G}═══════════════════════════════════════════════════${N}"

# Step 1: Install APK
log "Step 1: Opening APK installer..."
termux-open "$APK" 2>/dev/null || am start --user 0 -a android.intent.action.VIEW -d "file://$APK" -t application/vnd.android.package-archive 2>/dev/null || warn "Could not open installer — open manually: termux-open $APK"
echo -e "   ${Y}→ Tap 'Install' when the installer appears${N}"
sleep 1

# Step 2: Open Accessibility Settings
log "Step 2: Opening Accessibility settings..."
am start --user 0 -a android.settings.ACCESSIBILITY_SETTINGS 2>/dev/null || am start -a android.settings.ACCESSIBILITY_SETTINGS 2>/dev/null || warn "Open Settings → Accessibility manually"
echo -e "   ${Y}→ Find 'AccessDroid' under Downloaded apps${N}"
echo -e "   ${Y}→ Tap it → toggle ON → confirm in dialog${N}"
sleep 1

# Step 3: Open Overlay/Display over apps
log "Step 3: Opening 'Display over other apps' settings..."
am start --user 0 -a android.settings.action.MANAGE_OVERLAY_PERMISSION 2>/dev/null || warn "Open Settings → Apps → Special access → Display over other apps manually"
echo -e "   ${Y}→ Find Termux → toggle ON (if not already)${N}"

# Step 4: Open Battery optimization (ignore for background)
log "Step 4: Opening Battery optimization settings..."
am start --user 0 -a android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS 2>/dev/null || warn "Open Settings → Battery → Battery optimization manually"
echo -e "   ${Y}→ Find Termux → select 'Don't optimize'${N}"

# Step 5: Open Usage access (if needed)
log "Step 5: Opening 'Usage access' settings..."
am start --user 0 -a android.settings.USAGE_ACCESS_SETTINGS 2>/dev/null || warn "Open Settings → Apps → Special access → Usage access manually"
echo -e "   ${Y}→ Find Termux → toggle ON${N}"

echo ""
echo -e "${G}═══════════════════════════════════════════════════${N}"
echo -e "${Y}Manual steps above — toggle ON all permissions listed.${N}"
echo -e "${Y}Press ENTER after completing all 5 steps...${N}"
echo -e "${G}═══════════════════════════════════════════════════${N}"
read -p ""

# Step 6: Test AccessDroid
log "Step 6: Testing AccessDroid connection..."
RESULT=$(am broadcast --user 0 -a com.accessdroid.termux.ACTION_BACK --es secret termux-accessdroid-2025 2>&1)
if echo "$RESULT" | grep -q "Broadcast sent\|result=0"; then
    log "✓ AccessDroid broadcast works"
else
    warn "Broadcast may not have been received — check AccessibilityService is enabled"
fi

# Step 7: Enable termux-am socket (for amctl)
log "Step 7: Enabling termux-am socket..."
TP="$HOME/.termux/termux.properties"
if grep -q 'run-termux-am-socket-server' "$TP" 2>/dev/null; then
    sed -i 's/run-termux-am-socket-server=false/run-termux-am-socket-server=true/' "$TP"
else
    echo "run-termux-am-socket-server = true" >> "$TP"
fi
termux-reload-settings 2>/dev/null || true

# Step 8: Test amctl
log "Step 8: Testing amctl..."
if command -v amctl >/dev/null 2>&1; then
    amctl help 2>&1 | head -3 || warn "amctl not responding"
else
    warn "amctl not found — add ~/bin to PATH"
    echo 'export PATH="$HOME/bin:$PATH"' >> ~/.bashrc 2>/dev/null || true
fi

echo ""
echo -e "${G}═══════════════════════════════════════════════════${N}"
echo -e "${G}  SETUP COMPLETE                                  ${N}"
echo -e "${G}═══════════════════════════════════════════════════${N}"
echo ""
echo "Quick test commands:"
echo "  amctl tap 540 960        # tap center screen"
echo "  amctl screenshot         # capture screen"
echo "  amctl type 'hello'       # type into focused field"
echo "  amctl launch chrome      # launch an app"
echo ""
echo "Live view server:"
echo "  ~/bin/accessdroid/live-view-server.sh  # start HTTP bridge"
echo ""

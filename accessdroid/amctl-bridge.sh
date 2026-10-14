#!/data/data/com.termux/files/usr/bin/bash
# amctl-bridge — AccessDroid commands via am broadcast (no termux-am socket needed)
# Usage mirrors amctl: tap, swipe, type, click, scroll, back, home, recents, launch, screenshot, tree
set -euo pipefail
SECRET="termux-accessdroid-2025"
PKG="com.accessdroid.termux"

ac() {
    local action="$1"; shift
    /system/bin/am broadcast --user 0 -p "$PKG" -a "$action" --es secret "$SECRET" "$@" 2>&1
}

case "${1:-help}" in
    tap)        ac "com.accessdroid.termux.ACTION_TAP"   --ei x "${2:-0}" --ei y "${3:-0}" --el duration "${4:-80}";;
    longpress)  ac "com.accessdroid.termux.ACTION_LONG_PRESS" --ei x "${2:-0}" --ei y "${3:-0}" --el duration "${4:-500}";;
    swipe)      ac "com.accessdroid.termux.ACTION_SWIPE" --ei x1 "${2:-0}" --ei y1 "${3:-0}" --ei x2 "${4:-0}" --ei y2 "${5:-0}" --el duration "${6:-300}";;
    scroll)     local dir="${2:-down}"; [[ "$dir" == down ]] && ac "com.accessdroid.termux.ACTION_SCROLL_DOWN" --ei x "${3:-0}" --ei y "${4:-0}" || ac "com.accessdroid.termux.ACTION_SCROLL_UP" --ei x "${3:-0}" --ei y "${4:-0}";;
    back)       ac "com.accessdroid.termux.ACTION_BACK";;
    home)       ac "com.accessdroid.termux.ACTION_HOME";;
    recents)    ac "com.accessdroid.termux.ACTION_RECENTS";;
    type)       ac "com.accessdroid.termux.ACTION_TYPE" --es text "$2";;
    click)      ac "com.accessdroid.termux.ACTION_CLICK_TEXT" --es text "$2";;
    clickid)    ac "com.accessdroid.termux.ACTION_CLICK_ID" --es id "$2";;
    scrollto)   ac "com.accessdroid.termux.ACTION_SCROLL_TO" --es text "$2";;
    screenshot) ac "com.accessdroid.termux.ACTION_SCREENDUMP"; echo "Screenshot broadcast sent (check /sdcard/amctl-shot.png)";;
    tree)       ac "com.accessdroid.termux.ACTION_GET_UI_TREE"; echo "UI tree dumped to logcat (run: logcat -s AccessDroid)";;
    presskey)   local k="$1"; local code=0; case "$k" in back) code=4;; home) code=3;; recents) code=187;; *) echo "only back|home|recents"; exit 1;; esac; ac "com.accessdroid.termux.ACTION_PRESS_KEY" --ei keyCode "$code";;
    launch)     /system/bin/am start --user 0 -p "$2" -c android.intent.category.LAUNCHER -a android.intent.action.MAIN 2>&1;;
    list)       /system/bin/pm list packages -3 -f 2>/dev/null | sed 's/^package://' | awk -F'=' '{printf "%-45s %s\n",$1,$2}';;
    help|-h|--help)
        echo "Usage: amctl-bridge <command> [args...]"
        echo "Commands: tap X Y [ms] | longpress X Y [ms] | swipe X1 Y1 X2 Y2 [ms] | scroll up|down [x y]"
        echo "          type \"text\" | click \"text\" | clickid id | scrollto \"text\" | back | home | recents"
        echo "          screenshot | tree | presskey back|home|recents | launch <pkg> | list"
        ;;
    *) echo "unknown: $1 (use help)"; exit 1;;
esac

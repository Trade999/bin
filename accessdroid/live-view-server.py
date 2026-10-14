#!/data/data/com.termux/files/usr/bin/python3
"""
AccessDroid Live View — HTTP Bridge Server
Runs locally on port 8765 (configurable via --port).
The Pi coding agent connects here for screen operations.

Endpoints:
  GET  /status         — check if AccessDroid is alive
  GET  /screenshot     — capture screenshot (returns base64 PNG)
  POST /tap            — {"x": int, "y": int, "duration_ms": int}
  POST /swipe          — {"x1","y1","x2","y2","duration_ms"}
  POST /type           — {"text": str}
  POST /scroll         — {"direction": "up"|"down"}
  POST /click          — {"text": str}  — click UI element by text
  POST /press          — {"key": "back"|"home"|"recents"}
  POST /launch         — {"package": str} — launch an app
"""
import http.server, json, os, subprocess, sys, base64, time
from urllib.parse import urlparse, parse_qs

SECRET = "termux-accessdroid-2025"
PKG   = "com.accessdroid.termux"
HOST  = "127.0.0.1"
PORT  = int(os.environ.get("AD_PORT", 8765))

# ─── broadcast helper ──────────────────────────────────────────────────
def broadcast(action: str, extras: str = "") -> dict:
    """Send an am broadcast to AccessDroid and return the raw output."""
    cmd = [
        "/system/bin/am", "broadcast", "--user", "0",
        "-p", PKG,
        "-a", f"{PKG}.{action}",
        "--es", "secret", SECRET,
    ]
    if extras:
        cmd.extend(extras.split())
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=5)
    return {"ok": r.returncode == 0, "stdout": r.stdout.strip(), "stderr": r.stderr.strip()}

# ─── action functions ──────────────────────────────────────────────────
def do_tap(x, y, dur=80):
    return broadcast("ACTION_TAP", f"--ei x {x} --ei y {y} --el duration {dur}")

def do_longpress(x, y, dur=500):
    return broadcast("ACTION_LONG_PRESS", f"--ei x {x} --ei y {y} --el duration {dur}")

def do_swipe(x1, y1, x2, y2, dur=300):
    return broadcast("ACTION_SWIPE", f"--ei x1 {x1} --ei y1 {y1} --ei x2 {x2} --ei y2 {y2} --el duration {dur}")

def do_scroll(direction="down"):
    a = "ACTION_SCROLL_DOWN" if direction == "down" else "ACTION_SCROLL_UP"
    return broadcast(a)

def do_type(text):
    return broadcast("ACTION_TYPE", f'--es text "{text}"')

def do_click_text(text):
    return broadcast("ACTION_CLICK_TEXT", f'--es text "{text}"')

def do_click_id(rid):
    return broadcast("ACTION_CLICK_ID", f'--es id "{rid}"')

def do_press(key):
    m = {"back": 4, "home": 3, "recents": 187}
    code = m.get(key)
    if not code:
        return {"ok": False, "error": f"unknown key: {key}"}
    return broadcast("ACTION_PRESS_KEY", f"--ei keyCode {code}")

def do_launch(pkg):
    r = subprocess.run(
        ["/system/bin/am", "start", "--user", "0", "-c", "android.intent.category.LAUNCHER",
         "-a", "android.intent.action.MAIN", "-p", pkg],
        capture_output=True, text=True, timeout=5
    )
    return {"ok": r.returncode == 0, "stdout": r.stdout.strip()}

def do_screenshot():
    """Capture screenshot via AccessDroid's takeScreenshot API. Returns base64 of a PNG file
    written to /sdcard/amctl-shot.png by the service, or captures via screencap fallback."""
    # First try amctl screenshot broadcast (service writes to disk)
    broadcast("ACTION_SCREENDUMP")
    time.sleep(0.5)
    # Try reading the file AccessDroid might have written
    path = "/sdcard/amctl-shot.png"
    if os.path.exists(path):
        with open(path, "rb") as f:
            return {"ok": True, "base64": base64.b64encode(f.read()).decode(), "method": "accessdroid"}
    # Fallback: use screencap binary
    try:
        r = subprocess.run(["screencap", "-p"], capture_output=True, timeout=3)
        if r.returncode == 0 and len(r.stdout) > 100:
            return {"ok": True, "base64": base64.b64encode(r.stdout).decode(), "method": "screencap"}
    except Exception:
        pass
    return {"ok": False, "error": "screenshot failed — check AccessibilityService is enabled"}

def do_tree():
    broadcast("ACTION_GET_UI_TREE")
    return {"ok": True, "note": "UI tree dumped to logcat -s AccessDroid"}

# ─── HTTP server ───────────────────────────────────────────────────────
class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass  # quiet

    def _json(self, data, status=200):
        body = json.dumps(data).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read_body(self):
        length = int(self.headers.get("Content-Length", 0))
        if length == 0:
            return {}
        return json.loads(self.rfile.read(length))

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/status":
            # Quick check: can we reach AccessDroid?
            r = broadcast("ACTION_BACK")
            self._json({"alive": r.get("ok", False), "service": "accessdroid-live-view"})
        elif path == "/screenshot":
            self._json(do_screenshot())
        else:
            self._json({"error": "not found"}, 404)

    def do_POST(self):
        path = urlparse(self.path).path
        body = self._read_body()
        handlers = {
            "/tap":      lambda: do_tap(body.get("x", 0), body.get("y", 0), body.get("duration_ms", 80)),
            "/longpress": lambda: do_longpress(body.get("x", 0), body.get("y", 0), body.get("duration_ms", 500)),
            "/swipe":    lambda: do_swipe(body.get("x1", 0), body.get("y1", 0), body.get("x2", 0), body.get("y2", 0), body.get("duration_ms", 300)),
            "/scroll":   lambda: do_scroll(body.get("direction", "down")),
            "/type":     lambda: do_type(body.get("text", "")),
            "/click":    lambda: do_click_text(body.get("text", "")),
            "/clickid":  lambda: do_click_id(body.get("id", "")),
            "/press":    lambda: do_press(body.get("key", "")),
            "/launch":   lambda: do_launch(body.get("package", "")),
            "/tree":     lambda: do_tree(),
        }
        fn = handlers.get(path)
        if fn:
            self._json(fn())
        else:
            self._json({"error": "not found"}, 404)

if __name__ == "__main__":
    port = PORT
    if len(sys.argv) > 1:
        port = int(sys.argv[1])
    print(f"AccessDroid Live View Server — http://{HOST}:{port}")
    print(f"Endpoints: /status, /screenshot, /tap, /swipe, /type, /click, /scroll, /press, /launch, /tree")
    print(f"Secret: {SECRET}")
    server = http.server.HTTPServer((HOST, port), Handler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")

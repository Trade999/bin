#!/usr/bin/env python3
"""Serve AccessDroid APK download page — run in Termux, open device browser."""
import http.server, os, sys

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8787
DIR  = os.path.dirname(os.path.abspath(__file__))

os.chmod(os.path.join(DIR, "build", "AccessDroid.apk"), 0o644)

class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=DIR, **kw)
    def do_GET(self):
        if self.path == "/" or self.path == "/index.html":
            self.path = "/apk-download.html"
        return super().do_GET()
    def log_message(self, fmt, *a):
        print(f"  {a[0]}")

print(f"\n  ┌─────────────────────────────────────┐")
print(f"  │  AccessDroid APK Server              │")
print(f"  │  http://127.0.0.1:{PORT}              │")
print(f"  │  http://localhost:{PORT}              │")
print(f"  └─────────────────────────────────────┘")
print(f"  Open this URL in your device browser.\n")

http.server.HTTPServer(("0.0.0.0", PORT), Handler).serve_forever()

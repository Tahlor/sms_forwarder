#!/usr/bin/env python3
import os
import sys
from pathlib import Path

# Add server directory to sys.path
sys.path.insert(0, str(Path(__file__).parent))

from app import create_app, create_asgi_app
from app.config import config

# Expose both WSGI app and ASGI asgi_app for deployment flexibility
app = create_app()
asgi_app = create_asgi_app()

def main():
    host = config.host
    port = config.port

    print(f"[*] SMS Bridge starting on http://{host}:{port}")
    print(f"[*] Data directory: {config.data_dir.resolve()}")
    print(f"[*] Persistent DB: {config.db_path.resolve()}")
    print(f"[*] Bound to safe default host: {host}")

    # Check if ASGI server is requested or fallback to production Waitress WSGI
    use_asgi = os.environ.get("SMS_BRIDGE_SERVER_MODE", "").lower() in ("asgi", "uvicorn")
    if use_asgi:
        try:
            import uvicorn
            print("[*] Serving via Uvicorn ASGI server...")
            uvicorn.run(asgi_app, host=host, port=port)
            return
        except ImportError:
            print("[!] Uvicorn not installed, falling back to Waitress WSGI...")

    try:
        from waitress import serve
        print("[*] Serving via production Waitress WSGI server...")
        serve(app, host=host, port=port, threads=8)
    except ImportError:
        print("[*] Waitress not found, serving via Flask internal server...")
        app.run(host=host, port=port)

if __name__ == "__main__":
    main()

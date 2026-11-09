"""
ASGI entrypoint for SMS Bridge server.
Allows running with ASGI application servers such as Uvicorn, Hypercorn, or Granian.
Example:
    uvicorn app.asgi:app --host 127.0.0.1 --port 8765
"""
from . import create_asgi_app

app = create_asgi_app()

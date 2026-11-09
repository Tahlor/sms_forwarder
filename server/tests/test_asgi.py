import asyncio
import json
import pytest
from app import create_asgi_app

def test_asgi_health_endpoint(app_config):
    asgi = create_asgi_app(app_config)

    scope = {
        "type": "http",
        "asgi": {"version": "3.0", "spec_version": "2.4"},
        "http_version": "1.1",
        "method": "GET",
        "scheme": "http",
        "path": "/health",
        "raw_path": b"/health",
        "query_string": b"",
        "headers": [(b"host", b"localhost:8765")],
        "state": {}
    }

    messages_received = []

    async def receive():
        return {"type": "http.request", "body": b"", "more_body": False}

    async def send(message):
        messages_received.append(message)

    async def run():
        await asgi(scope, receive, send)

    asyncio.run(run())

    start_msg = next(m for m in messages_received if m["type"] == "http.response.start")
    body_msg = next(m for m in messages_received if m["type"] == "http.response.body")

    assert start_msg["status"] == 200
    # Verify security headers
    headers_dict = {k.decode("latin1").lower(): v.decode("latin1") for k, v in start_msg["headers"]}
    assert headers_dict.get("x-frame-options") == "DENY"
    assert "default-src 'self'" in headers_dict.get("content-security-policy", "")

    # Verify JSON response body
    data = json.loads(body_msg["body"].decode("utf-8"))
    assert data["status"] == "ok"
    assert data["service"] == "sms-bridge"

def test_asgi_pairing_claim_validation(app_config):
    asgi = create_asgi_app(app_config)

    body_bytes = json.dumps({"pairing_token": ""}).encode("utf-8")
    scope = {
        "type": "http",
        "asgi": {"version": "3.0", "spec_version": "2.4"},
        "http_version": "1.1",
        "method": "POST",
        "scheme": "http",
        "path": "/api/v1/pairing/claim",
        "raw_path": b"/api/v1/pairing/claim",
        "query_string": b"",
        "headers": [
            (b"host", b"localhost:8765"),
            (b"content-type", b"application/json"),
            (b"content-length", str(len(body_bytes)).encode("latin1"))
        ],
        "state": {}
    }

    messages_received = []

    async def receive():
        return {"type": "http.request", "body": body_bytes, "more_body": False}

    async def send(message):
        messages_received.append(message)

    async def run():
        await asgi(scope, receive, send)

    asyncio.run(run())

    start_msg = next(m for m in messages_received if m["type"] == "http.response.start")
    body_msg = next(m for m in messages_received if m["type"] == "http.response.body")

    assert start_msg["status"] == 400
    data = json.loads(body_msg["body"].decode("utf-8"))
    assert "error" in data

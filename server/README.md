# SMS Bridge Standalone Server

Secure, self-hosted, multi-phone SMS Bridge server. Allows managing multiple Android phones running the Connected edition of SMS Forwarder from a single centralized web dashboard, WSGI/ASGI application, and REST API.

## Core Features

- **Multi-Phone Isolation**: Unlimited Android phones on one server. Each phone has a unique `device_id`, secret token hash, human-readable label, dedicated message inbox/outbox, job queues, and revocation status.
- **Strict Conversation Isolation**: Conversations are strictly keyed by `(device_id, correspondent)`. If two different phones communicate with the same contact (e.g. `+1-555-1234`), they remain separate, isolated conversations in both the database and dashboard.
- **Zero Broadcast SMS**: Outbound SMS sending requires explicit selection of the source Android phone. There is no implicit broadcast.
- **Send Opt-In Control**: Outbound SMS sending is subject to phone-side opt-in. If a device has not opted into sending or has disabled remote sending, attempts to queue outbound SMS for that device are rejected.
- **Secure Pairing**: Short-lived (10-minute TTL), single-use random cryptographic tokens presented as an SVG QR code or manual pairing link. Claimed over TLS with phone-side user confirmation before storing credentials.
- **Event Ingestion & Deduplication**: Telemetry and diagnostic events (`sms_received`, `status`, `battery`, etc.) are ingested idempotently using cryptographic client fingerprints.
- **Queue Retry**: Outbound SMS jobs and notifications support queue retry with persistent `retry_count` tracking.
- **Push Notification Enqueueing**: Queue custom local notifications to specific phones with priority and delivery tracking.
- **Persistent SQLite WAL & Migrations**: High-performance SQLite database running in WAL mode with automated versioned schema migrations (`001_initial_schema.sql`, `002_events_and_retry.sql`).
- **ASGI & WSGI Compatibility**: Built with Flask 3 + `asgiref`, running under Waitress WSGI or ASGI servers (Uvicorn / Hypercorn / Granian) via `app.asgi:app` or `run:asgi_app`.
- **Hardened Security**:
  - Safe default bind to loopback (`127.0.0.1`).
  - No anonymous access to messages or device queues.
  - PBKDF2-HMAC-SHA256 password hashing (600,000 iterations).
  - CSRF protection on all administrative forms.
  - Strict Content-Security-Policy (CSP), `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`.
  - Automatic HTML escaping to eliminate XSS.

---

## Deployment Guide

### Option 1: Docker Compose (Recommended)

1. Navigate to the `server/` directory:
   ```bash
   cd server
   ```

2. Copy or edit `docker-compose.yml` and configure your environment variables:
   ```yaml
   ports:
     - "127.0.0.1:${SMS_BRIDGE_HOST_PORT:-8765}:8765"
   environment:
     - SMS_BRIDGE_HOST=0.0.0.0
     - SMS_BRIDGE_PORT=8765
     - SMS_BRIDGE_ADMIN_USER=admin
     - SMS_BRIDGE_ADMIN_PASSWORD=your-secure-admin-password
     - SMS_BRIDGE_SECRET_KEY=your-random-32-byte-hex-string
     - SMS_BRIDGE_PUBLIC_URL=https://sms.yourdomain.com
     - SMS_BRIDGE_SECURE_COOKIE=1
   ```

3. Launch the container:
   ```bash
   docker compose up -d --build
   ```

4. Verify status:
   ```bash
   docker compose ps
   docker compose logs -f
   ```

The container binds to loopback (`127.0.0.1`) by default, protecting it from public network exposure until configured behind a reverse proxy.

---

### Option 2: Native Python Run (WSGI or ASGI)

1. Ensure Python 3.12+ is installed.
2. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```
3. Set environment variables:
   ```bash
   export SMS_BRIDGE_HOST=127.0.0.1
   export SMS_BRIDGE_PORT=8765
   export SMS_BRIDGE_ADMIN_USER=admin
   export SMS_BRIDGE_ADMIN_PASSWORD="your-strong-password"
   export SMS_BRIDGE_SECRET_KEY="$(openssl rand -hex 32)"
   export SMS_BRIDGE_PUBLIC_URL="http://127.0.0.1:8765"
   ```
4. Run via production WSGI server:
   ```bash
   python3 run.py
   ```
5. Or run via an ASGI server (e.g. Uvicorn):
   ```bash
   uvicorn app.asgi:app --host 127.0.0.1 --port 8765
   ```

---

## API Reference

### Pairing
- `POST /api/v1/pairing/start`: (Admin) Generate short-lived pairing token and QR payload.
- `GET /api/v1/pairing/qr/<token>`: (Public) Render SVG QR code for unclaimed, unexpired token.
- `POST /api/v1/pairing/claim`: (Device) Claim pairing token; returns `device_id` and `device_token`.

### Device Operations (`X-Device-Id` and `X-Device-Token` headers required)
- `POST /api/v1/devices/<device_id>/messages`: Inbound SMS upload with deduplication.
- `GET /api/v1/devices/<device_id>/messages`: Query message list (`correspondent`, `direction`, `since`, `limit`).
- `GET /api/v1/devices/<device_id>/conversations`: List conversations grouped by correspondent.
- `GET /api/v1/devices/<device_id>/conversations/<correspondent>/messages`: Chronological thread messages.
- `POST /api/v1/devices/<device_id>/events`: Ingest telemetry/events (single or batch) with deduplication.
- `GET /api/v1/devices/<device_id>/events`: Query ingested events.
- `GET /api/v1/devices/<device_id>/jobs`: Poll outbound SMS dispatch jobs (`?status=pending`).
- `POST /api/v1/devices/<device_id>/jobs`: Enqueue outbound SMS job (enforces `send_opt_in`).
- `POST /api/v1/devices/<device_id>/jobs/<job_id>/ack`: Acknowledge job completion or carrier failure.
- `POST /api/v1/devices/<device_id>/jobs/<job_id>/retry`: Reset failed job to pending.
- `GET /api/v1/devices/<device_id>/notifications`: Poll push notification jobs.
- `POST /api/v1/devices/<device_id>/notifications`: Enqueue push notification job.
- `POST /api/v1/devices/<device_id>/notifications/<notif_id>/ack`: Acknowledge notification delivery.
- `POST /api/v1/devices/<device_id>/notifications/<notif_id>/retry`: Re-push notification job.
- `GET /api/v1/devices/<device_id>/settings`: Inspect device label and `send_opt_in` status.
- `POST /api/v1/devices/<device_id>/settings`: Update device label or toggle `send_opt_in`.
- `POST /api/v1/devices/<device_id>/revoke`: Device self-revocation.

### Administrative Operations (Admin session or Bearer token)
- `GET /api/v1/conversations`: Unified thread list across all devices (or filtered by `device_id`).
- `GET /api/v1/conversations/<device_id>/<correspondent>`: Unified thread message history.
- `GET /api/v1/jobs`: Unified outbound SMS dispatch jobs view.
- `POST /api/v1/jobs/<job_id>/retry`: Re-queue failed dispatch job.
- `POST /api/v1/devices/<device_id>/admin-revoke`: Revoke device access immediately.

---

## HTTPS Reverse Proxy Configuration

The server defaults to binding to loopback (`127.0.0.1:8765`). For production use with Android devices over the internet, place the server behind an HTTPS reverse proxy with a valid TLS certificate.

### Caddyfile Example
```caddy
sms.yourdomain.com {
    reverse_proxy 127.0.0.1:8765 {
        header_up X-Forwarded-Proto https
        header_up X-Real-IP {remote_host}
    }
}
```

### Nginx Example
```nginx
server {
    listen 443 ssl http2;
    server_name sms.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/sms.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/sms.yourdomain.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8765;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

---

## Security Invariants

1. **Isolation by Design**:
   - Device A cannot query, claim, or complete Device B's outbound jobs.
   - Device A cannot access Device B's messages, events, or notifications.
   - Requesting `/api/v1/devices/{device_id}/*` requires that `device_id` matches the authenticated `X-Device-Id`.

2. **One-Time Pairing Token**:
   - Tokens are 128-bit cryptographically random tokens (`ptok_...`).
   - Tokens expire after 600 seconds (configurable via `SMS_BRIDGE_PAIRING_TTL_SECONDS`).
   - Once claimed, the token is recorded with `claimed_at` timestamp and can never be claimed again.

3. **Immediate Revocation**:
   - When an administrator revokes a device in the dashboard or API, or a device self-revokes, `revoked_at` is set immediately.
   - All subsequent API requests from the revoked device return `403 Forbidden`.

4. **Privacy Boundaries**:
   - Live SMS sync is opt-in on the phone.
   - Extra SMS body and security code upload is OFF by default.
   - Remote sending via phone SIM is OFF by default and enforced by `send_opt_in`.

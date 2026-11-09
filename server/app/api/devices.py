import json
import secrets
import hashlib
from datetime import datetime, timezone
from flask import Blueprint, request, jsonify, g
from ..db import db_transaction, get_db
from ..auth import require_device, require_admin

devices_bp = Blueprint("api_devices", __name__, url_prefix="/api/v1/devices")

def get_device_metadata(dev) -> dict:
    raw = dev["metadata"] if isinstance(dev, dict) or hasattr(dev, "__getitem__") else "{}"
    if isinstance(raw, dict):
        return raw
    try:
        return json.loads(raw or "{}")
    except Exception:
        return {}

def is_send_opted_in(dev) -> bool:
    meta = get_device_metadata(dev)
    if meta.get("send_opt_in") is not True or meta.get("send_enabled") is False:
        return False
    return True

# ---------------------------------------------------------
# Messages and Conversations
# ---------------------------------------------------------

@devices_bp.route("/<device_id>/messages", methods=["POST"])
@require_device
def upload_message(device_id: str):
    data = request.get_json(silent=True) or {}
    sender = str(data.get("sender", "")).strip()
    body = str(data.get("body", ""))
    timestamp = data.get("timestamp")
    is_sec = 1 if data.get("is_security_code") else 0
    extracted_code = data.get("extracted_code")
    client_fp = data.get("client_fingerprint")

    if not sender:
        return jsonify({"error": "Missing sender"}), 400

    if timestamp is None:
        timestamp = int(datetime.now(timezone.utc).timestamp() * 1000)
    else:
        try:
            timestamp = int(timestamp)
        except (ValueError, TypeError):
            return jsonify({"error": "Invalid timestamp format"}), 400

    if not client_fp:
        # Canonical fingerprint: sha256(sender:timestamp:body)
        raw_key = f"{sender}:{timestamp}:{body}"
        client_fp = hashlib.sha256(raw_key.encode("utf-8")).hexdigest()

    with db_transaction() as db:
        cursor = db.cursor()
        cursor.execute(
            "SELECT id FROM messages WHERE device_id = ? AND client_fingerprint = ?;",
            (device_id, client_fp)
        )
        existing = cursor.fetchone()
        if existing:
            # Idempotent deduplication
            return jsonify({
                "status": "duplicate",
                "message_id": existing["id"],
                "deduplicated": True
            }), 200

        msg_id = f"msg_{secrets.token_hex(12)}"
        db.execute(
            """
            INSERT INTO messages (
                id, device_id, direction, correspondent, body, timestamp,
                is_security_code, extracted_code, client_fingerprint, created_at
            ) VALUES (?, ?, 'inbound', ?, ?, ?, ?, ?, ?, datetime('now'));
            """,
            (msg_id, device_id, sender, body, timestamp, is_sec, extracted_code, client_fp)
        )

    return jsonify({
        "status": "created",
        "message_id": msg_id,
        "deduplicated": False
    }), 201

@devices_bp.route("/<device_id>/messages", methods=["GET"])
@require_device
def list_messages(device_id: str):
    corr = request.args.get("correspondent")
    direction = request.args.get("direction")
    since = request.args.get("since")
    order = request.args.get("order", "asc").lower()
    sort_dir = "DESC" if order == "desc" else "ASC"

    try:
        limit = min(int(request.args.get("limit", 50)), 200)
        offset = max(int(request.args.get("offset", 0)), 0)
    except (ValueError, TypeError):
        limit = 50
        offset = 0

    db = get_db()
    cursor = db.cursor()
    query = "SELECT id, device_id, direction, correspondent, body, timestamp, is_security_code, extracted_code, created_at FROM messages WHERE device_id = ?"
    params = [device_id]

    if corr:
        query += " AND correspondent = ?"
        params.append(corr)
    if direction in ("inbound", "outbound"):
        query += " AND direction = ?"
        params.append(direction)
    if since:
        try:
            query += " AND timestamp >= ?"
            params.append(int(since))
        except (ValueError, TypeError):
            pass

    query += f" ORDER BY timestamp {sort_dir} LIMIT ? OFFSET ?;"
    params.extend([limit, offset])

    cursor.execute(query, tuple(params))
    rows = cursor.fetchall()
    msgs = []
    for r in rows:
        msgs.append({
            "id": r["id"],
            "device_id": r["device_id"],
            "direction": r["direction"],
            "correspondent": r["correspondent"],
            "body": r["body"],
            "timestamp": r["timestamp"],
            "is_security_code": bool(r["is_security_code"]),
            "extracted_code": r["extracted_code"],
            "created_at": r["created_at"]
        })

    return jsonify({"messages": msgs}), 200

@devices_bp.route("/<device_id>/conversations", methods=["GET"])
@require_device
def list_conversations(device_id: str):
    try:
        limit = min(int(request.args.get("limit", 50)), 200)
        offset = max(int(request.args.get("offset", 0)), 0)
    except (ValueError, TypeError):
        limit = 50
        offset = 0

    db = get_db()
    cursor = db.cursor()
    cursor.execute(
        """
        SELECT correspondent, COUNT(*) as msg_count, MAX(timestamp) as last_timestamp
        FROM messages
        WHERE device_id = ?
        GROUP BY correspondent
        ORDER BY last_timestamp DESC
        LIMIT ? OFFSET ?;
        """,
        (device_id, limit, offset)
    )
    rows = cursor.fetchall()
    convs = []
    for r in rows:
        corr = r["correspondent"]
        cursor.execute(
            """
            SELECT body, direction, is_security_code, extracted_code, timestamp
            FROM messages
            WHERE device_id = ? AND correspondent = ?
            ORDER BY timestamp DESC, created_at DESC LIMIT 1;
            """,
            (device_id, corr)
        )
        last_m = cursor.fetchone()
        convs.append({
            "correspondent": corr,
            "msg_count": r["msg_count"],
            "last_timestamp": r["last_timestamp"],
            "last_body": last_m["body"] if last_m else "",
            "last_direction": last_m["direction"] if last_m else "",
            "last_is_security_code": bool(last_m["is_security_code"]) if last_m else False,
            "last_extracted_code": last_m["extracted_code"] if last_m else None
        })

    return jsonify({"conversations": convs}), 200

@devices_bp.route("/<device_id>/conversations/<path:correspondent>/messages", methods=["GET"])
@require_device
def get_conversation_thread(device_id: str, correspondent: str):
    db = get_db()
    cursor = db.cursor()
    cursor.execute(
        """
        SELECT id, device_id, direction, correspondent, body, timestamp, is_security_code, extracted_code, created_at
        FROM messages
        WHERE device_id = ? AND correspondent = ?
        ORDER BY timestamp ASC, created_at ASC;
        """,
        (device_id, correspondent)
    )
    rows = cursor.fetchall()
    msgs = [{
        "id": r["id"],
        "device_id": r["device_id"],
        "direction": r["direction"],
        "correspondent": r["correspondent"],
        "body": r["body"],
        "timestamp": r["timestamp"],
        "is_security_code": bool(r["is_security_code"]),
        "extracted_code": r["extracted_code"],
        "created_at": r["created_at"]
    } for r in rows]

    return jsonify({
        "device_id": device_id,
        "correspondent": correspondent,
        "messages": msgs
    }), 200

# ---------------------------------------------------------
# Event Ingestion & Deduplication
# ---------------------------------------------------------

@devices_bp.route("/<device_id>/events", methods=["POST"])
@require_device
def ingest_events(device_id: str):
    data = request.get_json(silent=True)
    if data is None:
        return jsonify({"error": "Invalid JSON body"}), 400

    if isinstance(data, dict):
        if "events" in data and isinstance(data["events"], list):
            event_items = data["events"]
            is_batch = True
        else:
            event_items = [data]
            is_batch = False
    elif isinstance(data, list):
        event_items = data
        is_batch = True
    else:
        return jsonify({"error": "Payload must be a JSON object or array"}), 400

    if not event_items:
        return jsonify({"error": "No events provided"}), 400

    results = []
    with db_transaction() as db:
        cursor = db.cursor()
        for ev in event_items:
            if not isinstance(ev, dict):
                results.append({"status": "error", "error": "Event item must be an object"})
                continue

            event_type = str(ev.get("event_type", "")).strip()
            if not event_type:
                results.append({"status": "error", "error": "Missing event_type"})
                continue

            timestamp = ev.get("timestamp")
            if timestamp is None:
                timestamp = int(datetime.now(timezone.utc).timestamp() * 1000)
            else:
                try:
                    timestamp = int(timestamp)
                except (ValueError, TypeError):
                    timestamp = int(datetime.now(timezone.utc).timestamp() * 1000)

            payload = ev.get("payload", ev.get("details", {}))
            payload_str = json.dumps(payload, sort_keys=True) if isinstance(payload, (dict, list)) else str(payload)

            client_fp = ev.get("client_fingerprint")
            if not client_fp:
                raw_fp = f"{event_type}:{timestamp}:{payload_str}"
                client_fp = hashlib.sha256(raw_fp.encode("utf-8")).hexdigest()

            # Check duplicate event
            cursor.execute(
                "SELECT id FROM device_events WHERE device_id = ? AND client_fingerprint = ?;",
                (device_id, client_fp)
            )
            existing = cursor.fetchone()
            if existing:
                results.append({
                    "status": "duplicate",
                    "event_id": existing["id"],
                    "deduplicated": True
                })
                continue

            event_id = f"evt_{secrets.token_hex(12)}"
            cursor.execute(
                """
                INSERT INTO device_events (id, device_id, event_type, payload, timestamp, client_fingerprint, created_at)
                VALUES (?, ?, ?, ?, ?, ?, datetime('now'));
                """,
                (event_id, device_id, event_type, payload_str, timestamp, client_fp)
            )

            # If event is an incoming SMS event, also record into messages table
            if event_type in ("sms_received", "sms_inbound") and isinstance(payload, dict):
                sender = str(payload.get("sender", "")).strip()
                body = str(payload.get("body", ""))
                if sender:
                    is_sec = 1 if payload.get("is_security_code") else 0
                    ext_code = payload.get("extracted_code")
                    msg_fp = payload.get("client_fingerprint") or client_fp
                    cursor.execute(
                        "SELECT id FROM messages WHERE device_id = ? AND client_fingerprint = ?;",
                        (device_id, msg_fp)
                    )
                    if not cursor.fetchone():
                        msg_id = f"msg_{secrets.token_hex(12)}"
                        cursor.execute(
                            """
                            INSERT INTO messages (
                                id, device_id, direction, correspondent, body, timestamp,
                                is_security_code, extracted_code, client_fingerprint, created_at
                            ) VALUES (?, ?, 'inbound', ?, ?, ?, ?, ?, ?, datetime('now'));
                            """,
                            (msg_id, device_id, sender, body, timestamp, is_sec, ext_code, msg_fp)
                        )

            results.append({
                "status": "created",
                "event_id": event_id,
                "deduplicated": False
            })

    if not is_batch:
        single = results[0]
        status_code = 200 if single["status"] == "duplicate" else (201 if single["status"] == "created" else 400)
        return jsonify(single), status_code

    all_created = all(r.get("status") == "created" for r in results)
    status_code = 201 if all_created else 200
    return jsonify({"events": results}), status_code

@devices_bp.route("/<device_id>/events", methods=["GET"])
@require_device
def list_events(device_id: str):
    event_type = request.args.get("event_type")
    since = request.args.get("since")
    try:
        limit = min(int(request.args.get("limit", 50)), 200)
    except (ValueError, TypeError):
        limit = 50

    db = get_db()
    cursor = db.cursor()
    query = "SELECT id, event_type, payload, timestamp, client_fingerprint, created_at FROM device_events WHERE device_id = ?"
    params = [device_id]

    if event_type:
        query += " AND event_type = ?"
        params.append(event_type)
    if since:
        try:
            query += " AND timestamp >= ?"
            params.append(int(since))
        except (ValueError, TypeError):
            pass

    query += " ORDER BY timestamp DESC LIMIT ?;"
    params.append(limit)

    cursor.execute(query, tuple(params))
    rows = cursor.fetchall()
    events = []
    for r in rows:
        try:
            p = json.loads(r["payload"])
        except Exception:
            p = r["payload"]
        events.append({
            "id": r["id"],
            "event_type": r["event_type"],
            "payload": p,
            "timestamp": r["timestamp"],
            "client_fingerprint": r["client_fingerprint"],
            "created_at": r["created_at"]
        })

    return jsonify({"events": events}), 200

# ---------------------------------------------------------
# Outbound Jobs (Explicit-Device Queue & Retry)
# ---------------------------------------------------------

@devices_bp.route("/<device_id>/jobs", methods=["GET"])
@require_device
def list_jobs(device_id: str):
    status_filter = request.args.get("status", "pending").lower()
    allowed_statuses = ("pending", "claimed", "completed", "failed", "all")
    if status_filter not in allowed_statuses:
        status_filter = "pending"

    db = get_db()
    cursor = db.cursor()
    if status_filter == "all":
        cursor.execute(
            """
            SELECT id, recipient, body, status, created_at, updated_at, ack_details, retry_count
            FROM outbound_jobs
            WHERE device_id = ?
            ORDER BY created_at ASC;
            """,
            (device_id,)
        )
    else:
        cursor.execute(
            """
            SELECT id, recipient, body, status, created_at, updated_at, ack_details, retry_count
            FROM outbound_jobs
            WHERE device_id = ? AND status = ?
            ORDER BY created_at ASC;
            """,
            (device_id, status_filter)
        )

    rows = cursor.fetchall()
    jobs = []
    for r in rows:
        jobs.append({
            "id": r["id"],
            "recipient": r["recipient"],
            "body": r["body"],
            "status": r["status"],
            "created_at": r["created_at"],
            "updated_at": r["updated_at"],
            "ack_details": json.loads(r["ack_details"]) if r["ack_details"] else None,
            "retry_count": r["retry_count"] if "retry_count" in r.keys() else 0
        })

    return jsonify({"jobs": jobs}), 200

@devices_bp.route("/<device_id>/jobs", methods=["POST"])
@require_device
def enqueue_outbound_job(device_id: str):
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT id, label, revoked_at, metadata FROM devices WHERE id = ?;", (device_id,))
    dev = cursor.fetchone()
    if not dev:
        return jsonify({"error": "Device not found"}), 404
    if dev["revoked_at"] is not None:
        return jsonify({"error": "Device access revoked"}), 403

    if not is_send_opted_in(dev):
        return jsonify({"error": "Device has not opted into outbound SMS sending"}), 403

    data = request.get_json(silent=True) or {}
    recipient = str(data.get("recipient", "")).strip()
    body = str(data.get("body", "")).strip()

    if not recipient or not body:
        return jsonify({"error": "Missing recipient or body"}), 400

    job_id = f"job_{secrets.token_hex(8)}"
    msg_id = f"msg_{secrets.token_hex(12)}"
    now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)

    with db_transaction() as tx:
        tx.execute(
            """
            INSERT INTO outbound_jobs (id, device_id, recipient, body, status, created_at, updated_at, retry_count)
            VALUES (?, ?, ?, ?, 'pending', datetime('now'), datetime('now'), 0);
            """,
            (job_id, device_id, recipient, body)
        )
        tx.execute(
            """
            INSERT INTO messages (
                id, device_id, direction, correspondent, body, timestamp, created_at
            ) VALUES (?, ?, 'outbound', ?, ?, ?, datetime('now'));
            """,
            (msg_id, device_id, recipient, body, now_ms)
        )

    return jsonify({
        "status": "queued",
        "job_id": job_id,
        "recipient": recipient,
        "message_id": msg_id
    }), 201

@devices_bp.route("/<device_id>/jobs/<job_id>/ack", methods=["POST"])
@require_device
def acknowledge_job(device_id: str, job_id: str):
    data = request.get_json(silent=True) or {}
    new_status = data.get("status", "completed").lower()
    if new_status not in ("completed", "failed"):
        new_status = "completed"

    ack_details = {
        "status": new_status,
        "carrier_ack": data.get("carrier_ack"),
        "error": data.get("error"),
        "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000)
    }

    with db_transaction() as db:
        cursor = db.cursor()
        cursor.execute(
            "SELECT id, status FROM outbound_jobs WHERE id = ? AND device_id = ?;",
            (job_id, device_id)
        )
        row = cursor.fetchone()
        if not row:
            return jsonify({"error": "Job not found"}), 404

        # Update job status idempotently
        db.execute(
            """
            UPDATE outbound_jobs
            SET status = ?, updated_at = datetime('now'), ack_details = ?
            WHERE id = ? AND device_id = ?;
            """,
            (new_status, json.dumps(ack_details), job_id, device_id)
        )

    return jsonify({
        "status": "acknowledged",
        "job_id": job_id,
        "job_status": new_status
    }), 200

@devices_bp.route("/<device_id>/jobs/<job_id>/retry", methods=["POST"])
@require_device
def retry_job(device_id: str, job_id: str):
    with db_transaction() as db:
        cursor = db.cursor()
        cursor.execute(
            "SELECT id, status, retry_count FROM outbound_jobs WHERE id = ? AND device_id = ?;",
            (job_id, device_id)
        )
        row = cursor.fetchone()
        if not row:
            return jsonify({"error": "Job not found"}), 404

        new_retry_count = (row["retry_count"] or 0) + 1
        db.execute(
            """
            UPDATE outbound_jobs
            SET status = 'pending', retry_count = ?, updated_at = datetime('now')
            WHERE id = ? AND device_id = ?;
            """,
            (new_retry_count, job_id, device_id)
        )

    return jsonify({
        "status": "retried",
        "job_id": job_id,
        "retry_count": new_retry_count,
        "job_status": "pending"
    }), 200

# ---------------------------------------------------------
# Notifications (Push Jobs & Retry)
# ---------------------------------------------------------

@devices_bp.route("/<device_id>/notifications", methods=["GET"])
@require_device
def list_notifications(device_id: str):
    status_filter = request.args.get("status", "pending").lower()
    if status_filter not in ("pending", "completed", "failed", "all"):
        status_filter = "pending"

    db = get_db()
    cursor = db.cursor()
    if status_filter == "all":
        cursor.execute(
            """
            SELECT id, title, body, priority, status, created_at, updated_at, retry_count
            FROM notification_jobs
            WHERE device_id = ?
            ORDER BY created_at ASC;
            """,
            (device_id,)
        )
    else:
        cursor.execute(
            """
            SELECT id, title, body, priority, status, created_at, updated_at, retry_count
            FROM notification_jobs
            WHERE device_id = ? AND status = ?
            ORDER BY created_at ASC;
            """,
            (device_id, status_filter)
        )

    rows = cursor.fetchall()
    notifications = []
    for r in rows:
        notifications.append({
            "id": r["id"],
            "title": r["title"],
            "body": r["body"],
            "priority": r["priority"],
            "status": r["status"],
            "created_at": r["created_at"],
            "retry_count": r["retry_count"] if "retry_count" in r.keys() else 0
        })

    return jsonify({"notifications": notifications}), 200

@devices_bp.route("/<device_id>/notifications", methods=["POST"])
@require_device
def enqueue_notification(device_id: str):
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT id, label, revoked_at FROM devices WHERE id = ?;", (device_id,))
    dev = cursor.fetchone()
    if not dev:
        return jsonify({"error": "Device not found"}), 404
    if dev["revoked_at"] is not None:
        return jsonify({"error": "Device access revoked"}), 403

    data = request.get_json(silent=True) or {}
    title = str(data.get("title", "")).strip()
    body = str(data.get("body", "")).strip()
    priority = str(data.get("priority", "normal")).lower()
    if priority not in ("low", "normal", "high"):
        priority = "normal"

    if not title or not body:
        return jsonify({"error": "Missing title or body"}), 400

    notif_id = f"notif_{secrets.token_hex(8)}"
    with db_transaction() as tx:
        tx.execute(
            """
            INSERT INTO notification_jobs (id, device_id, title, body, priority, status, created_at, updated_at, retry_count)
            VALUES (?, ?, ?, ?, ?, 'pending', datetime('now'), datetime('now'), 0);
            """,
            (notif_id, device_id, title, body, priority)
        )

    return jsonify({
        "status": "queued",
        "notification_id": notif_id
    }), 201

@devices_bp.route("/<device_id>/notifications/<notif_id>/ack", methods=["POST"])
@require_device
def acknowledge_notification(device_id: str, notif_id: str):
    data = request.get_json(silent=True) or {}
    outcome = str(data.get("status", "completed")).lower()
    if outcome not in ("completed", "failed"):
        outcome = "failed"
    ack_details = {
        "status": outcome,
        "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000),
        "details": data.get("details")
    }

    with db_transaction() as db:
        cursor = db.cursor()
        cursor.execute(
            "SELECT id FROM notification_jobs WHERE id = ? AND device_id = ?;",
            (notif_id, device_id)
        )
        row = cursor.fetchone()
        if not row:
            return jsonify({"error": "Notification not found"}), 404

        db.execute(
            """
            UPDATE notification_jobs
            SET status = ?, updated_at = datetime('now'), ack_details = ?
            WHERE id = ? AND device_id = ?;
            """,
            (outcome, json.dumps(ack_details), notif_id, device_id)
        )

    return jsonify({
        "status": "acknowledged",
        "notification_id": notif_id
    }), 200

@devices_bp.route("/<device_id>/notifications/<notif_id>/retry", methods=["POST"])
@require_device
def retry_notification(device_id: str, notif_id: str):
    with db_transaction() as db:
        cursor = db.cursor()
        cursor.execute(
            "SELECT id, status, retry_count FROM notification_jobs WHERE id = ? AND device_id = ?;",
            (notif_id, device_id)
        )
        row = cursor.fetchone()
        if not row:
            return jsonify({"error": "Notification not found"}), 404

        new_retry_count = (row["retry_count"] or 0) + 1
        db.execute(
            """
            UPDATE notification_jobs
            SET status = 'pending', retry_count = ?, updated_at = datetime('now')
            WHERE id = ? AND device_id = ?;
            """,
            (new_retry_count, notif_id, device_id)
        )

    return jsonify({
        "status": "retried",
        "notification_id": notif_id,
        "retry_count": new_retry_count,
        "job_status": "pending"
    }), 200

# ---------------------------------------------------------
# Settings and Capabilities (Send Opt-In)
# ---------------------------------------------------------

@devices_bp.route("/<device_id>/settings", methods=["GET", "POST", "PATCH"])
@require_device
def device_settings(device_id: str):
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT id, label, revoked_at, metadata FROM devices WHERE id = ?;", (device_id,))
    dev = cursor.fetchone()
    if not dev:
        return jsonify({"error": "Device not found"}), 404

    meta = get_device_metadata(dev)

    if request.method in ("POST", "PATCH"):
        data = request.get_json(silent=True) or {}
        new_label = data.get("label")
        if new_label is not None:
            new_label = str(new_label).strip()
        else:
            new_label = dev["label"]

        if "send_opt_in" in data:
            meta["send_opt_in"] = bool(data["send_opt_in"])
        if "metadata" in data and isinstance(data["metadata"], dict):
            meta.update(data["metadata"])

        with db_transaction() as tx:
            tx.execute(
                "UPDATE devices SET label = ?, metadata = ?, last_seen_at = datetime('now') WHERE id = ?;",
                (new_label, json.dumps(meta), device_id)
            )

        return jsonify({
            "status": "updated",
            "device_id": device_id,
            "label": new_label,
            "send_opt_in": meta.get("send_opt_in", False),
            "metadata": meta
        }), 200

    return jsonify({
        "device_id": device_id,
        "label": dev["label"],
        "send_opt_in": meta.get("send_opt_in", False),
        "metadata": meta
    }), 200

# ---------------------------------------------------------
# Revocation
# ---------------------------------------------------------

@devices_bp.route("/<device_id>/revoke", methods=["POST"])
@require_device
def self_revoke(device_id: str):
    with db_transaction() as db:
        db.execute(
            "UPDATE devices SET revoked_at = datetime('now') WHERE id = ?;",
            (device_id,)
        )
        db.execute(
            """
            INSERT INTO audit_logs (actor_type, actor_id, action, target_id, details)
            VALUES ('device', ?, 'self_revocation', ?, '{}');
            """,
            (device_id, device_id)
        )

    return jsonify({
        "status": "revoked",
        "device_id": device_id
    }), 200

@devices_bp.route("/<device_id>/admin-revoke", methods=["POST"])
@require_admin
def admin_api_revoke(device_id: str):
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT id, label FROM devices WHERE id = ?;", (device_id,))
    dev = cursor.fetchone()
    if not dev:
        return jsonify({"error": "Device not found"}), 404

    with db_transaction() as tx:
        tx.execute("UPDATE devices SET revoked_at = datetime('now') WHERE id = ?;", (device_id,))
        tx.execute(
            """
            INSERT INTO audit_logs (actor_type, actor_id, action, target_id, details)
            VALUES ('admin', 'admin', 'admin_api_revoke', ?, ?);
            """,
            (device_id, json.dumps({"label": dev["label"]}))
        )

    return jsonify({
        "status": "revoked",
        "device_id": device_id
    }), 200

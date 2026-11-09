import json
import secrets
import urllib.parse
from datetime import datetime, timezone, timedelta
from flask import Blueprint, request, jsonify, Response
from ..config import config, get_config
from ..db import db_transaction, get_db
from ..auth import require_admin, hash_device_token
from ..qr import generate_qr_svg

pairing_bp = Blueprint("api_pairing", __name__, url_prefix="/api/v1/pairing")

@pairing_bp.route("/start", methods=["POST"])
@require_admin
def start_pairing():
    data = request.get_json(silent=True) or {}
    label_hint = data.get("label_hint", "").strip()

    cfg = get_config()
    token = f"ptok_{secrets.token_hex(16)}"
    now = datetime.now(timezone.utc)
    expires_at = now + timedelta(seconds=cfg.pairing_ttl_seconds)
    expires_str = expires_at.strftime("%Y-%m-%d %H:%M:%S")

    with db_transaction() as db:
        db.execute(
            "INSERT INTO pairing_tokens (token, expires_at) VALUES (?, ?);",
            (token, expires_str)
        )
        db.execute(
            "INSERT INTO audit_logs (actor_type, actor_id, action, details) VALUES ('admin', 'admin', 'started_pairing', ?);",
            (json.dumps({"token_prefix": token[:10], "label_hint": label_hint}),)
        )

    server_url = cfg.public_url
    encoded_server = urllib.parse.quote(server_url, safe="")
    qr_payload = f"smsbridge://pair?server={encoded_server}&token={token}"
    if label_hint:
        qr_payload += f"&label={urllib.parse.quote(label_hint)}"

    return jsonify({
        "pairing_token": token,
        "expires_at": expires_at.isoformat(),
        "ttl_seconds": cfg.pairing_ttl_seconds,
        "server_url": server_url,
        "qr_payload": qr_payload,
        "qr_svg_url": f"/api/v1/pairing/qr/{token}"
    }), 201

@pairing_bp.route("/qr/<token>", methods=["GET"])
def get_qr_svg(token: str):
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT token, expires_at, claimed_at FROM pairing_tokens WHERE token = ?;", (token,))
    row = cursor.fetchone()
    if not row:
        return Response("Pairing token not found", status=404, mimetype="text/plain")

    now_str = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    if row["claimed_at"] is not None:
        return Response("Pairing token already claimed", status=410, mimetype="text/plain")
    if row["expires_at"] < now_str:
        return Response("Pairing token expired", status=410, mimetype="text/plain")

    server_url = get_config().public_url
    encoded_server = urllib.parse.quote(server_url, safe="")
    qr_payload = f"smsbridge://pair?server={encoded_server}&token={token}"
    svg = generate_qr_svg(qr_payload)
    return Response(svg, mimetype="image/svg+xml")

@pairing_bp.route("/claim", methods=["POST"])
def claim_pairing():
    data = request.get_json(silent=True) or {}
    token = data.get("pairing_token", "").strip()
    device_label = data.get("device_label", "").strip() or "Android Phone"
    metadata = data.get("metadata", {})

    if not token:
        return jsonify({"error": "Missing pairing_token"}), 400

    now_str = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")

    with db_transaction() as db:
        cursor = db.cursor()
        cursor.execute("SELECT token, expires_at, claimed_at FROM pairing_tokens WHERE token = ?;", (token,))
        row = cursor.fetchone()

        if not row:
            return jsonify({"error": "Invalid pairing token"}), 404

        if row["claimed_at"] is not None:
            return jsonify({"error": "Pairing token has already been claimed"}), 409

        if row["expires_at"] < now_str:
            return jsonify({"error": "Pairing token has expired"}), 410

        device_id = f"dev_{secrets.token_hex(8)}"
        device_token = f"dtok_{secrets.token_hex(24)}"
        token_hash = hash_device_token(device_token)

        meta_json = json.dumps(metadata) if isinstance(metadata, dict) else "{}"

        # Insert new device
        db.execute(
            """
            INSERT INTO devices (id, label, token_hash, created_at, last_seen_at, metadata)
            VALUES (?, ?, ?, datetime('now'), datetime('now'), ?);
            """,
            (device_id, device_label, token_hash, meta_json)
        )

        # Mark token as claimed
        db.execute(
            "UPDATE pairing_tokens SET claimed_at = datetime('now'), device_id = ? WHERE token = ?;",
            (device_id, token)
        )

        # Record audit log
        db.execute(
            """
            INSERT INTO audit_logs (actor_type, actor_id, action, target_id, details)
            VALUES ('device', ?, 'claimed_pairing', ?, ?);
            """,
            (device_id, device_id, json.dumps({"label": device_label}))
        )

    return jsonify({
        "status": "success",
        "device_id": device_id,
        "device_token": device_token,
        "device_label": device_label,
        "server_url": get_config().public_url
    }), 201

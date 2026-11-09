import json
from flask import Blueprint, request, jsonify
from ..db import db_transaction, get_db
from ..auth import require_admin

conversations_bp = Blueprint("api_conversations", __name__, url_prefix="/api/v1")

@conversations_bp.route("/conversations", methods=["GET"])
@require_admin
def list_all_conversations():
    device_id = request.args.get("device_id")
    try:
        limit = min(int(request.args.get("limit", 50)), 200)
        offset = max(int(request.args.get("offset", 0)), 0)
    except (ValueError, TypeError):
        limit = 50
        offset = 0

    db = get_db()
    cursor = db.cursor()
    if device_id and device_id != "all":
        cursor.execute(
            """
            SELECT m.device_id, d.label as device_label, m.correspondent,
                   COUNT(*) as msg_count, MAX(m.timestamp) as last_timestamp
            FROM messages m
            JOIN devices d ON d.id = m.device_id
            WHERE m.device_id = ?
            GROUP BY m.device_id, m.correspondent
            ORDER BY last_timestamp DESC
            LIMIT ? OFFSET ?;
            """,
            (device_id, limit, offset)
        )
    else:
        cursor.execute(
            """
            SELECT m.device_id, d.label as device_label, m.correspondent,
                   COUNT(*) as msg_count, MAX(m.timestamp) as last_timestamp
            FROM messages m
            JOIN devices d ON d.id = m.device_id
            GROUP BY m.device_id, m.correspondent
            ORDER BY last_timestamp DESC
            LIMIT ? OFFSET ?;
            """,
            (limit, offset)
        )

    rows = cursor.fetchall()
    convs = []
    for r in rows:
        dev_id = r["device_id"]
        corr = r["correspondent"]
        cursor.execute(
            """
            SELECT body, direction, is_security_code, extracted_code, timestamp
            FROM messages
            WHERE device_id = ? AND correspondent = ?
            ORDER BY timestamp DESC, created_at DESC LIMIT 1;
            """,
            (dev_id, corr)
        )
        last_m = cursor.fetchone()
        convs.append({
            "device_id": dev_id,
            "device_label": r["device_label"],
            "correspondent": corr,
            "msg_count": r["msg_count"],
            "last_timestamp": r["last_timestamp"],
            "last_body": last_m["body"] if last_m else "",
            "last_direction": last_m["direction"] if last_m else "",
            "last_is_security_code": bool(last_m["is_security_code"]) if last_m else False,
            "last_extracted_code": last_m["extracted_code"] if last_m else None
        })

    return jsonify({"conversations": convs}), 200

@conversations_bp.route("/conversations/<device_id>/<path:correspondent>", methods=["GET"])
@require_admin
def get_admin_conversation_thread(device_id: str, correspondent: str):
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT id, label FROM devices WHERE id = ?;", (device_id,))
    dev = cursor.fetchone()
    if not dev:
        return jsonify({"error": "Device not found"}), 404

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
        "device_label": dev["label"],
        "correspondent": correspondent,
        "messages": msgs
    }), 200

@conversations_bp.route("/jobs", methods=["GET"])
@require_admin
def list_admin_jobs():
    device_id = request.args.get("device_id")
    status_filter = request.args.get("status")
    try:
        limit = min(int(request.args.get("limit", 50)), 200)
    except (ValueError, TypeError):
        limit = 50

    db = get_db()
    cursor = db.cursor()
    query = """
        SELECT j.id, j.device_id, d.label as device_label, j.recipient, j.body,
               j.status, j.created_at, j.updated_at, j.ack_details, j.retry_count
        FROM outbound_jobs j
        JOIN devices d ON d.id = j.device_id
        WHERE 1=1
    """
    params = []
    if device_id and device_id != "all":
        query += " AND j.device_id = ?"
        params.append(device_id)
    if status_filter:
        query += " AND j.status = ?"
        params.append(status_filter)

    query += " ORDER BY j.created_at DESC LIMIT ?;"
    params.append(limit)

    cursor.execute(query, tuple(params))
    rows = cursor.fetchall()
    jobs = []
    for r in rows:
        jobs.append({
            "id": r["id"],
            "device_id": r["device_id"],
            "device_label": r["device_label"],
            "recipient": r["recipient"],
            "body": r["body"],
            "status": r["status"],
            "created_at": r["created_at"],
            "updated_at": r["updated_at"],
            "ack_details": json.loads(r["ack_details"]) if r["ack_details"] else None,
            "retry_count": r["retry_count"] if "retry_count" in r.keys() else 0
        })

    return jsonify({"jobs": jobs}), 200

@conversations_bp.route("/jobs/<job_id>/retry", methods=["POST"])
@require_admin
def retry_admin_job(job_id: str):
    with db_transaction() as db:
        cursor = db.cursor()
        cursor.execute("SELECT id, device_id, status, retry_count FROM outbound_jobs WHERE id = ?;", (job_id,))
        row = cursor.fetchone()
        if not row:
            return jsonify({"error": "Job not found"}), 404

        new_retry_count = (row["retry_count"] or 0) + 1
        db.execute(
            """
            UPDATE outbound_jobs
            SET status = 'pending', retry_count = ?, updated_at = datetime('now')
            WHERE id = ?;
            """,
            (new_retry_count, job_id)
        )
        db.execute(
            """
            INSERT INTO audit_logs (actor_type, actor_id, action, target_id, details)
            VALUES ('admin', 'admin', 'retry_job', ?, ?);
            """,
            (job_id, json.dumps({"retry_count": new_retry_count}))
        )

    return jsonify({
        "status": "retried",
        "job_id": job_id,
        "retry_count": new_retry_count,
        "job_status": "pending"
    }), 200

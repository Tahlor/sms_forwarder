import json
import secrets
from datetime import datetime, timezone
from flask import Blueprint, request, session, redirect, url_for, flash, abort, render_template_string, Response
from ..config import config, get_config
from ..db import db_transaction, get_db
from ..auth import require_admin, require_csrf, verify_password, get_admin_password_hash, get_csrf_token
from .templates import template_env

admin_bp = Blueprint("web_admin", __name__)

def format_epoch_ms(epoch_ms: int) -> str:
    if not epoch_ms:
        return ""
    try:
        dt = datetime.fromtimestamp(epoch_ms / 1000.0, tz=timezone.utc)
        return dt.strftime("%Y-%m-%d %H:%M:%S UTC")
    except Exception:
        return str(epoch_ms)

@admin_bp.route("/login", methods=["GET", "POST"])
def login():
    if session.get("is_admin"):
        return redirect(url_for("web_admin.dashboard"))

    if request.method == "POST":
        username = request.form.get("username", "").strip()
        password = request.form.get("password", "")

        # Verify CSRF
        csrf_tok = request.form.get("csrf_token")
        if not csrf_tok or csrf_tok != session.get("csrf_token"):
            flash("Invalid or expired session security token. Please try again.", "danger")
            template = template_env.get_template("login.html")
            return Response(template.render(csrf_token=get_csrf_token), mimetype="text/html")

        stored_hash = get_admin_password_hash()
        cfg = get_config()
        if username == cfg.admin_user and verify_password(password, stored_hash):
            session.clear()
            session["is_admin"] = True
            session["admin_user"] = username
            session["csrf_token"] = secrets.token_hex(32)
            flash("Logged in successfully.", "success")
            next_url = request.args.get("next") or url_for("web_admin.dashboard")
            return redirect(next_url)
        else:
            flash("Invalid username or password.", "danger")

    template = template_env.get_template("login.html")
    return Response(template.render(csrf_token=get_csrf_token), mimetype="text/html")

@admin_bp.route("/logout", methods=["POST"])
@require_admin
@require_csrf
def logout():
    session.clear()
    flash("Logged out successfully.", "success")
    return redirect(url_for("web_admin.login"))

@admin_bp.route("/", methods=["GET"])
@require_admin
def dashboard():
    selected_device_id = request.args.get("device_id", "all")
    view_device = request.args.get("view_device")
    view_corr = request.args.get("view_corr")

    db = get_db()
    cursor = db.cursor()

    # 1. Fetch all devices and parse metadata
    cursor.execute("SELECT id, label, token_hash, created_at, last_seen_at, revoked_at, metadata FROM devices ORDER BY created_at DESC;")
    raw_devices = [dict(r) for r in cursor.fetchall()]
    all_devices = []
    for d in raw_devices:
        try:
            meta = json.loads(d["metadata"] or "{}")
        except Exception:
            meta = {}
        d["send_opt_in"] = meta.get("send_opt_in") is True and meta.get("send_enabled") is not False
        all_devices.append(d)

    active_devices = [d for d in all_devices if not d["revoked_at"]]

    # 2. Fetch conversation threads grouped by (device_id, correspondent)
    if selected_device_id == "all":
        cursor.execute("""
            SELECT m.device_id, d.label as device_label, m.correspondent,
                   COUNT(*) as msg_count,
                   MAX(m.timestamp) as last_ts
            FROM messages m
            JOIN devices d ON d.id = m.device_id
            GROUP BY m.device_id, m.correspondent
            ORDER BY last_ts DESC;
        """)
    else:
        cursor.execute("""
            SELECT m.device_id, d.label as device_label, m.correspondent,
                   COUNT(*) as msg_count,
                   MAX(m.timestamp) as last_ts
            FROM messages m
            JOIN devices d ON d.id = m.device_id
            WHERE m.device_id = ?
            GROUP BY m.device_id, m.correspondent
            ORDER BY last_ts DESC;
        """, (selected_device_id,))

    thread_rows = cursor.fetchall()
    threads = []
    for r in thread_rows:
        dev_id = r["device_id"]
        corr = r["correspondent"]
        # Fetch the latest message for preview
        cursor.execute("""
            SELECT body, is_security_code, timestamp
            FROM messages
            WHERE device_id = ? AND correspondent = ?
            ORDER BY timestamp DESC, created_at DESC
            LIMIT 1;
        """, (dev_id, corr))
        last_msg = cursor.fetchone()
        threads.append({
            "device_id": dev_id,
            "device_label": r["device_label"],
            "correspondent": corr,
            "msg_count": r["msg_count"],
            "last_ts": r["last_ts"],
            "formatted_time": format_epoch_ms(r["last_ts"]),
            "last_body": last_msg["body"] if last_msg else "",
            "last_is_sec": last_msg["is_security_code"] if last_msg else 0
        })

    # 3. If a specific thread is selected, fetch its messages
    active_thread = None
    thread_messages = []
    if view_device and view_corr:
        cursor.execute("SELECT id, label FROM devices WHERE id = ?;", (view_device,))
        d_row = cursor.fetchone()
        if d_row:
            active_thread = {
                "device_id": view_device,
                "device_label": d_row["label"],
                "correspondent": view_corr
            }
            cursor.execute("""
                SELECT id, direction, body, timestamp, is_security_code, extracted_code, created_at
                FROM messages
                WHERE device_id = ? AND correspondent = ?
                ORDER BY timestamp ASC, created_at ASC;
            """, (view_device, view_corr))
            for m in cursor.fetchall():
                thread_messages.append({
                    "id": m["id"],
                    "direction": m["direction"],
                    "body": m["body"],
                    "timestamp": m["timestamp"],
                    "formatted_time": format_epoch_ms(m["timestamp"]),
                    "is_security_code": m["is_security_code"],
                    "extracted_code": m["extracted_code"]
                })

    # 4. Fetch recent outbound jobs and notifications
    if selected_device_id == "all":
        cursor.execute("""
            SELECT j.id, j.device_id, d.label as device_label, j.recipient, j.body,
                   j.status, j.created_at, j.updated_at, j.retry_count
            FROM outbound_jobs j
            JOIN devices d ON d.id = j.device_id
            ORDER BY j.created_at DESC LIMIT 15;
        """)
    else:
        cursor.execute("""
            SELECT j.id, j.device_id, d.label as device_label, j.recipient, j.body,
                   j.status, j.created_at, j.updated_at, j.retry_count
            FROM outbound_jobs j
            JOIN devices d ON d.id = j.device_id
            WHERE j.device_id = ?
            ORDER BY j.created_at DESC LIMIT 15;
        """, (selected_device_id,))
    recent_jobs = [dict(r) for r in cursor.fetchall()]

    if selected_device_id == "all":
        cursor.execute("""
            SELECT n.id, n.device_id, d.label as device_label, n.title, n.body,
                   n.priority, n.status, n.created_at, n.updated_at, n.retry_count
            FROM notification_jobs n
            JOIN devices d ON d.id = n.device_id
            ORDER BY n.created_at DESC LIMIT 15;
        """)
    else:
        cursor.execute("""
            SELECT n.id, n.device_id, d.label as device_label, n.title, n.body,
                   n.priority, n.status, n.created_at, n.updated_at, n.retry_count
            FROM notification_jobs n
            JOIN devices d ON d.id = n.device_id
            WHERE n.device_id = ?
            ORDER BY n.created_at DESC LIMIT 15;
        """, (selected_device_id,))
    recent_notifications = [dict(r) for r in cursor.fetchall()]

    template = template_env.get_template("dashboard.html")
    rendered = template.render(
        devices=all_devices,
        active_devices=active_devices,
        selected_device_id=selected_device_id,
        threads=threads,
        active_thread=active_thread,
        thread_messages=thread_messages,
        recent_jobs=recent_jobs,
        recent_notifications=recent_notifications,
        prefill_recipient=view_corr if active_thread else None,
        csrf_token=get_csrf_token
    )
    return Response(rendered, mimetype="text/html")

@admin_bp.route("/send", methods=["POST"])
@require_admin
@require_csrf
def send_sms():
    device_id = request.form.get("device_id", "").strip()
    recipient = request.form.get("recipient", "").strip()
    body = request.form.get("body", "").strip()

    if not device_id or device_id == "all":
        flash("You MUST explicitly select a specific source phone to send the SMS. Implicit broadcast is prohibited.", "danger")
        return redirect(url_for("web_admin.dashboard"))

    if not recipient or not body:
        flash("Recipient and message body are required.", "danger")
        return redirect(url_for("web_admin.dashboard", device_id=device_id))

    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT id, label, revoked_at, metadata FROM devices WHERE id = ?;", (device_id,))
    dev = cursor.fetchone()
    if not dev:
        flash("Selected device not found.", "danger")
        return redirect(url_for("web_admin.dashboard"))
    if dev["revoked_at"] is not None:
        flash("Selected device is revoked.", "danger")
        return redirect(url_for("web_admin.dashboard"))

    try:
        meta = json.loads(dev["metadata"] or "{}")
    except Exception:
        meta = {}
    if meta.get("send_opt_in") is not True or meta.get("send_enabled") is False:
        flash(f"Device '{dev['label']}' has not opted into outbound SMS sending.", "danger")
        return redirect(url_for("web_admin.dashboard", device_id=device_id))

    job_id = f"job_{secrets.token_hex(8)}"
    msg_id = f"msg_{secrets.token_hex(12)}"
    now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)

    with db_transaction() as tx:
        # Enqueue outbound job
        tx.execute(
            """
            INSERT INTO outbound_jobs (id, device_id, recipient, body, status, created_at, updated_at, retry_count)
            VALUES (?, ?, ?, ?, 'pending', datetime('now'), datetime('now'), 0);
            """,
            (job_id, device_id, recipient, body)
        )
        # Record in messages
        tx.execute(
            """
            INSERT INTO messages (
                id, device_id, direction, correspondent, body, timestamp, created_at
            ) VALUES (?, ?, 'outbound', ?, ?, ?, datetime('now'));
            """,
            (msg_id, device_id, recipient, body, now_ms)
        )
        tx.execute(
            """
            INSERT INTO audit_logs (actor_type, actor_id, action, target_id, details)
            VALUES ('admin', 'admin', 'enqueue_sms_job', ?, ?);
            """,
            (job_id, json.dumps({"device_id": device_id, "recipient": recipient}))
        )

    flash(f"Outbound SMS queued for {dev['label']}.", "success")
    return redirect(url_for("web_admin.dashboard", device_id=device_id, view_device=device_id, view_corr=recipient))

@admin_bp.route("/jobs/<job_id>/retry", methods=["POST"])
@require_admin
@require_csrf
def retry_job(job_id: str):
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT id, device_id, retry_count FROM outbound_jobs WHERE id = ?;", (job_id,))
    row = cursor.fetchone()
    if not row:
        flash("Job not found.", "danger")
        return redirect(url_for("web_admin.dashboard"))

    new_retry_count = (row["retry_count"] or 0) + 1
    with db_transaction() as tx:
        tx.execute(
            "UPDATE outbound_jobs SET status = 'pending', retry_count = ?, updated_at = datetime('now') WHERE id = ?;",
            (new_retry_count, job_id)
        )
        tx.execute(
            "INSERT INTO audit_logs (actor_type, actor_id, action, target_id, details) VALUES ('admin', 'admin', 'retry_job', ?, ?);",
            (job_id, json.dumps({"retry_count": new_retry_count}))
        )

    flash("Outbound SMS job re-queued for sending.", "success")
    return redirect(url_for("web_admin.dashboard", device_id=row["device_id"]))

@admin_bp.route("/notifications", methods=["POST"])
@require_admin
@require_csrf
def send_notification():
    device_id = request.form.get("device_id", "").strip()
    title = request.form.get("title", "").strip()
    body = request.form.get("body", "").strip()
    priority = request.form.get("priority", "normal").lower()

    if priority not in ("low", "normal", "high"):
        priority = "normal"

    if not device_id or not title or not body:
        flash("Target device, title, and body are required for notification.", "danger")
        return redirect(url_for("web_admin.dashboard"))

    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT id, label, revoked_at FROM devices WHERE id = ?;", (device_id,))
    dev = cursor.fetchone()
    if not dev or dev["revoked_at"] is not None:
        flash("Invalid or revoked target device.", "danger")
        return redirect(url_for("web_admin.dashboard"))

    notif_id = f"notif_{secrets.token_hex(8)}"
    with db_transaction() as tx:
        tx.execute(
            """
            INSERT INTO notification_jobs (id, device_id, title, body, priority, status, created_at, updated_at, retry_count)
            VALUES (?, ?, ?, ?, ?, 'pending', datetime('now'), datetime('now'), 0);
            """,
            (notif_id, device_id, title, body, priority)
        )
        tx.execute(
            """
            INSERT INTO audit_logs (actor_type, actor_id, action, target_id, details)
            VALUES ('admin', 'admin', 'enqueue_notification', ?, ?);
            """,
            (notif_id, json.dumps({"device_id": device_id, "title": title}))
        )

    flash(f"Notification enqueued for {dev['label']}.", "success")
    return redirect(url_for("web_admin.dashboard", device_id=device_id))

@admin_bp.route("/notifications/<notif_id>/retry", methods=["POST"])
@require_admin
@require_csrf
def retry_notification(notif_id: str):
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT id, device_id, retry_count FROM notification_jobs WHERE id = ?;", (notif_id,))
    row = cursor.fetchone()
    if not row:
        flash("Notification not found.", "danger")
        return redirect(url_for("web_admin.dashboard"))

    new_retry_count = (row["retry_count"] or 0) + 1
    with db_transaction() as tx:
        tx.execute(
            "UPDATE notification_jobs SET status = 'pending', retry_count = ?, updated_at = datetime('now') WHERE id = ?;",
            (new_retry_count, notif_id)
        )
        tx.execute(
            "INSERT INTO audit_logs (actor_type, actor_id, action, target_id, details) VALUES ('admin', 'admin', 'retry_notification', ?, ?);",
            (notif_id, json.dumps({"retry_count": new_retry_count}))
        )

    flash("Push notification re-queued.", "success")
    return redirect(url_for("web_admin.dashboard", device_id=row["device_id"]))

@admin_bp.route("/devices/<device_id>/revoke", methods=["POST"])
@require_admin
@require_csrf
def revoke_device(device_id: str):
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT id, label FROM devices WHERE id = ?;", (device_id,))
    dev = cursor.fetchone()
    if not dev:
        flash("Device not found.", "danger")
        return redirect(url_for("web_admin.dashboard"))

    with db_transaction() as tx:
        tx.execute("UPDATE devices SET revoked_at = datetime('now') WHERE id = ?;", (device_id,))
        tx.execute(
            """
            INSERT INTO audit_logs (actor_type, actor_id, action, target_id, details)
            VALUES ('admin', 'admin', 'revoked_device', ?, ?);
            """,
            (device_id, json.dumps({"label": dev["label"]}))
        )

    flash(f"Device '{dev['label']}' access has been revoked.", "warning")
    return redirect(url_for("web_admin.dashboard"))

import os
import hmac
import hashlib
import secrets
from functools import wraps
from flask import request, session, abort, jsonify, redirect, url_for, g
from .config import config, get_config
from .db import get_db

ITERATIONS = 600000

def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    key = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, ITERATIONS)
    return f"pbkdf2:sha256:{ITERATIONS}${salt.hex()}${key.hex()}"

def verify_password(password: str, stored_hash: str) -> bool:
    try:
        parts = stored_hash.split("$")
        if len(parts) != 3:
            return False
        algo_iter, salt_hex, key_hex = parts
        method, algo, iters_str = algo_iter.split(":")
        if method != "pbkdf2" or algo != "sha256":
            return False
        iters = int(iters_str)
        salt = bytes.fromhex(salt_hex)
        expected_key = bytes.fromhex(key_hex)
        derived_key = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, iters)
        return hmac.compare_digest(derived_key, expected_key)
    except Exception:
        return False

def hash_device_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()

def verify_device_token(token: str, expected_hash: str) -> bool:
    derived = hash_device_token(token)
    return hmac.compare_digest(derived, expected_hash)

def get_admin_password_hash() -> str:
    cfg = get_config()
    if cfg.admin_password_hash:
        return cfg.admin_password_hash
    if cfg.admin_password:
        return hash_password(cfg.admin_password)
    # Check if a persistent initial secret file exists
    secret_file = cfg.data_dir / ".admin_initial_secret"
    if secret_file.exists():
        stored = secret_file.read_text(encoding="utf-8").strip()
        if stored.startswith("pbkdf2:"):
            return stored
        return hash_password(stored)
    # Generate new random password, write hash to file
    rand_pwd = secrets.token_urlsafe(24)
    pwd_hash = hash_password(rand_pwd)
    try:
        cfg.data_dir.mkdir(parents=True, exist_ok=True)
        secret_file.write_text(pwd_hash, encoding="utf-8")
        secret_file.chmod(0o600)
    except Exception:
        pass
    return pwd_hash

def get_csrf_token() -> str:
    if "csrf_token" not in session:
        session["csrf_token"] = secrets.token_hex(32)
    return session["csrf_token"]

def validate_csrf_token(token: str | None) -> bool:
    session_token = session.get("csrf_token")
    if not session_token or not token:
        return False
    return hmac.compare_digest(session_token, token)

def require_admin(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        # 1. Check active session
        if session.get("is_admin"):
            return f(*args, **kwargs)
        
        # 2. Check API Bearer token
        auth_header = request.headers.get("Authorization", "")
        if auth_header.startswith("Bearer "):
            token = auth_header[7:].strip()
            # If an admin API token is set in config
            admin_api_token = os.environ.get("SMS_BRIDGE_ADMIN_API_TOKEN")
            if admin_api_token and hmac.compare_digest(token, admin_api_token):
                return f(*args, **kwargs)

        if request.path.startswith("/api/"):
            return jsonify({"error": "Unauthorized admin access required"}), 401
        return redirect(url_for("web_admin.login", next=request.path))
    return decorated

def require_csrf(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if request.method in ("POST", "PUT", "DELETE", "PATCH"):
            # Check header X-CSRF-Token or form parameter csrf_token
            token = request.headers.get("X-CSRF-Token") or request.form.get("csrf_token")
            if not validate_csrf_token(token):
                if request.path.startswith("/api/"):
                    return jsonify({"error": "Invalid or missing CSRF token"}), 403
                abort(403, description="CSRF verification failed: missing or invalid CSRF token")
        return f(*args, **kwargs)
    return decorated

def require_device(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        device_id = request.headers.get("X-Device-Id")
        device_token = request.headers.get("X-Device-Token")
        
        # Support Bearer token format as fallback: Authorization: Bearer <device_token>
        if not device_token:
            auth_header = request.headers.get("Authorization", "")
            if auth_header.startswith("Bearer "):
                device_token = auth_header[7:].strip()

        if not device_id or not device_token:
            return jsonify({"error": "Missing device authentication credentials (X-Device-Id / X-Device-Token)"}), 401

        # Check if URL parameter device_id matches header device_id
        url_device_id = kwargs.get("device_id")
        if url_device_id and url_device_id != device_id:
            return jsonify({"error": "Forbidden: Device ID mismatch"}), 403

        db = get_db()
        cursor = db.cursor()
        cursor.execute("SELECT id, label, token_hash, revoked_at, metadata FROM devices WHERE id = ?;", (device_id,))
        device = cursor.fetchone()

        if not device:
            return jsonify({"error": "Device not found"}), 401

        if device["revoked_at"] is not None:
            return jsonify({"error": "Device access revoked"}), 403

        if not verify_device_token(device_token, device["token_hash"]):
            return jsonify({"error": "Invalid device token"}), 401

        # Update last seen timestamp
        db.execute("UPDATE devices SET last_seen_at = datetime('now') WHERE id = ?;", (device_id,))
        db.commit()

        g.current_device = dict(device)
        return f(*args, **kwargs)
    return decorated

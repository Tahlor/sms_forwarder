import pytest
import secrets
from pathlib import Path
from app import create_app
from app.config import Config
from app.auth import hash_password, hash_device_token
from app.db import init_db, db_transaction

@pytest.fixture
def app_config(tmp_path: Path):
    cfg = Config()
    cfg.data_dir = tmp_path
    cfg.db_path = tmp_path / "test_sms_bridge.db"
    cfg.secret_key = "test-secret-key-12345"
    cfg.admin_user = "admin"
    cfg.admin_password = "correct-horse-battery-staple"
    cfg.admin_password_hash = hash_password("correct-horse-battery-staple")
    cfg.public_url = "http://127.0.0.1:8765"
    cfg.pairing_ttl_seconds = 600
    cfg.cookie_secure = False
    return cfg

@pytest.fixture
def app(app_config):
    app = create_app(app_config)
    app.config["TESTING"] = True
    return app

@pytest.fixture
def client(app):
    return app.test_client()

@pytest.fixture
def admin_client(client, app_config):
    # Fetch CSRF token from login page
    res = client.get("/login")
    assert res.status_code == 200
    with client.session_transaction() as sess:
        csrf = sess.get("csrf_token")
    # Log in
    res = client.post("/login", data={
        "username": app_config.admin_user,
        "password": "correct-horse-battery-staple",
        "csrf_token": csrf
    }, follow_redirects=True)
    assert res.status_code == 200
    return client

@pytest.fixture
def paired_devices(app_config):
    dev1_id = "dev_phone1"
    dev1_token = f"dtok_{secrets.token_hex(24)}"
    dev1_hash = hash_device_token(dev1_token)

    dev2_id = "dev_phone2"
    dev2_token = f"dtok_{secrets.token_hex(24)}"
    dev2_hash = hash_device_token(dev2_token)

    with db_transaction() as db:
        db.execute(
            """
            INSERT INTO devices (id, label, token_hash, created_at, last_seen_at, metadata)
            VALUES (?, 'Pixel 8 (Work)', ?, datetime('now'), datetime('now'), '{"send_opt_in":true}');
            """,
            (dev1_id, dev1_hash)
        )
        db.execute(
            """
            INSERT INTO devices (id, label, token_hash, created_at, last_seen_at, metadata)
            VALUES (?, 'Galaxy S23 (Personal)', ?, datetime('now'), datetime('now'), '{"send_opt_in":true}');
            """,
            (dev2_id, dev2_hash)
        )

    return {
        "dev1": {"id": dev1_id, "token": dev1_token, "label": "Pixel 8 (Work)"},
        "dev2": {"id": dev2_id, "token": dev2_token, "label": "Galaxy S23 (Personal)"}
    }

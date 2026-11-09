import pytest
from app.auth import hash_password, verify_password

def test_password_hashing():
    pwd = "super-secret-password-123"
    hashed = hash_password(pwd)
    assert hashed.startswith("pbkdf2:sha256:600000$")
    assert verify_password(pwd, hashed) is True
    assert verify_password("wrong-password", hashed) is False
    assert verify_password("", hashed) is False

def test_admin_login_success_and_failure(client, app_config):
    # Fetch login page to get CSRF token
    res = client.get("/login")
    assert res.status_code == 200
    with client.session_transaction() as sess:
        csrf = sess.get("csrf_token")
    assert csrf is not None

    # Failed login: wrong password
    res = client.post("/login", data={
        "username": app_config.admin_user,
        "password": "wrong-password",
        "csrf_token": csrf
    })
    assert res.status_code == 200
    assert b"Invalid username or password" in res.data

    # Successful login
    res = client.post("/login", data={
        "username": app_config.admin_user,
        "password": "correct-horse-battery-staple",
        "csrf_token": csrf
    }, follow_redirects=False)
    assert res.status_code == 302
    assert res.location.endswith("/") or res.location.endswith("/dashboard")

    # Access protected dashboard
    res = client.get("/")
    assert res.status_code == 200
    assert b"Connected Phones" in res.data

def test_anonymous_access_denied(client):
    # Dashboard redirects to login
    res = client.get("/", follow_redirects=False)
    assert res.status_code == 302
    assert "/login" in res.location

    # Device endpoint rejected without credentials
    res = client.get("/api/v1/devices/dev_unknown/jobs")
    assert res.status_code == 401
    assert res.json.get("error") is not None

    # Pairing start rejected without admin
    res = client.post("/api/v1/pairing/start", json={})
    assert res.status_code == 401

def test_csrf_protection_on_post(client, admin_client):
    # Valid CSRF token succeeds
    with admin_client.session_transaction() as sess:
        valid_csrf = sess.get("csrf_token")

    res = admin_client.post("/send", data={
        "device_id": "all",
        "recipient": "+15551234",
        "body": "Hello",
        "csrf_token": valid_csrf
    }, follow_redirects=True)
    # Rejection because device_id='all' is prohibited (explicit device required)
    assert b"You MUST explicitly select a specific source phone" in res.data

    # Missing or invalid CSRF token fails with 403
    res = admin_client.post("/send", data={
        "device_id": "some_id",
        "recipient": "+15551234",
        "body": "Hello",
        "csrf_token": "invalid_forged_csrf_token"
    })
    assert res.status_code == 403

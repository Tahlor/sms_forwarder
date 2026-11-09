import pytest
from datetime import datetime, timezone, timedelta
from app.db import db_transaction

def test_pairing_lifecycle_and_one_use(admin_client):
    # 1. Admin starts pairing
    res = admin_client.post("/api/v1/pairing/start", json={"label_hint": "Pixel 8"})
    assert res.status_code == 201
    data = res.json
    token = data["pairing_token"]
    assert token.startswith("ptok_")
    assert "smsbridge://pair" in data["qr_payload"]
    qr_url = data["qr_svg_url"]

    # 2. Verify SVG QR code retrieval
    res_qr = admin_client.get(qr_url)
    assert res_qr.status_code == 200
    assert "image/svg+xml" in res_qr.content_type
    assert b"<svg" in res_qr.data

    # 3. Phone claims pairing token
    claim_payload = {
        "pairing_token": token,
        "device_label": "Pixel 8 (Work Phone)",
        "metadata": {"model": "Pixel 8", "sdk": 35}
    }
    res_claim = admin_client.post("/api/v1/pairing/claim", json=claim_payload)
    assert res_claim.status_code == 201
    claim_data = res_claim.json
    assert claim_data["status"] == "success"
    device_id = claim_data["device_id"]
    device_token = claim_data["device_token"]
    assert device_id.startswith("dev_")
    assert device_token.startswith("dtok_")
    assert claim_data["device_label"] == "Pixel 8 (Work Phone)"

    # 4. Attempt to re-claim same pairing token (MUST FAIL: One-use guarantee)
    res_reclaim = admin_client.post("/api/v1/pairing/claim", json=claim_payload)
    assert res_reclaim.status_code == 409
    assert "already been claimed" in res_reclaim.json["error"]

    # 5. QR code after claim returns 410 Gone
    res_qr_after = admin_client.get(qr_url)
    assert res_qr_after.status_code == 410

def test_pairing_token_ttl_expiration(admin_client):
    # Create an already expired token directly in DB
    expired_token = "ptok_expired_12345678"
    past_time = (datetime.now(timezone.utc) - timedelta(seconds=120)).strftime("%Y-%m-%d %H:%M:%S")

    with db_transaction() as db:
        db.execute(
            "INSERT INTO pairing_tokens (token, expires_at) VALUES (?, ?);",
            (expired_token, past_time)
        )

    # Claim attempt should return 410 Expired
    res = admin_client.post("/api/v1/pairing/claim", json={
        "pairing_token": expired_token,
        "device_label": "Old Token Phone"
    })
    assert res.status_code == 410
    assert "expired" in res.json["error"]

def test_pairing_invalid_token(admin_client):
    res = admin_client.post("/api/v1/pairing/claim", json={
        "pairing_token": "ptok_nonexistent_token_00000",
        "device_label": "Test Phone"
    })
    assert res.status_code == 404

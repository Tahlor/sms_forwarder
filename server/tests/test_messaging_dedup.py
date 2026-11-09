import pytest
from app.db import get_db

def test_inbound_message_upload_and_deduplication(admin_client, paired_devices):
    dev1 = paired_devices["dev1"]
    headers = {
        "X-Device-Id": dev1["id"],
        "X-Device-Token": dev1["token"]
    }

    payload = {
        "sender": "+15558888",
        "body": "Your security code is 987654. Do not share.",
        "timestamp": 1731172000000,
        "is_security_code": True,
        "extracted_code": "987654",
        "client_fingerprint": "client_fp_custom_001"
    }

    # 1. Initial upload
    res1 = admin_client.post(f"/api/v1/devices/{dev1['id']}/messages", headers=headers, json=payload)
    assert res1.status_code == 201
    data1 = res1.json
    assert data1["status"] == "created"
    assert data1["deduplicated"] is False
    msg_id = data1["message_id"]

    # 2. Duplicate upload with same client_fingerprint
    res2 = admin_client.post(f"/api/v1/devices/{dev1['id']}/messages", headers=headers, json=payload)
    assert res2.status_code == 200
    data2 = res2.json
    assert data2["status"] == "duplicate"
    assert data2["deduplicated"] is True
    assert data2["message_id"] == msg_id

    # 3. Verify exactly one record in database
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT COUNT(*) FROM messages WHERE device_id = ? AND client_fingerprint = ?;",
                   (dev1["id"], "client_fp_custom_001"))
    count = cursor.fetchone()[0]
    assert count == 1

def test_auto_fingerprint_dedup(admin_client, paired_devices):
    dev1 = paired_devices["dev1"]
    headers = {
        "X-Device-Id": dev1["id"],
        "X-Device-Token": dev1["token"]
    }

    # Omit client_fingerprint; server computes canonical sha256(sender:ts:body)
    payload = {
        "sender": "+15553333",
        "body": "Test message without client fingerprint",
        "timestamp": 1731173000000
    }

    res1 = admin_client.post(f"/api/v1/devices/{dev1['id']}/messages", headers=headers, json=payload)
    assert res1.status_code == 201
    msg_id = res1.json["message_id"]

    res2 = admin_client.post(f"/api/v1/devices/{dev1['id']}/messages", headers=headers, json=payload)
    assert res2.status_code == 200
    assert res2.json["deduplicated"] is True
    assert res2.json["message_id"] == msg_id

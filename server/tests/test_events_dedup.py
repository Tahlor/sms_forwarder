import pytest
from app.db import get_db

def test_single_event_ingestion_and_deduplication(admin_client, paired_devices):
    dev1 = paired_devices["dev1"]
    headers = {"X-Device-Id": dev1["id"], "X-Device-Token": dev1["token"]}

    payload = {
        "event_type": "diagnostic",
        "payload": {"level": "info", "message": "Service started"},
        "timestamp": 1731175000000,
        "client_fingerprint": "diag_fp_001"
    }

    # 1. Ingest event
    res1 = admin_client.post(f"/api/v1/devices/{dev1['id']}/events", headers=headers, json=payload)
    assert res1.status_code == 201
    data1 = res1.json
    assert data1["status"] == "created"
    assert data1["deduplicated"] is False
    event_id = data1["event_id"]

    # 2. Duplicate ingestion with same fingerprint -> returns 200 with deduplicated=True
    res2 = admin_client.post(f"/api/v1/devices/{dev1['id']}/events", headers=headers, json=payload)
    assert res2.status_code == 200
    data2 = res2.json
    assert data2["status"] == "duplicate"
    assert data2["deduplicated"] is True
    assert data2["event_id"] == event_id

    # 3. Verify exactly 1 record in device_events table
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT COUNT(*) FROM device_events WHERE device_id = ? AND client_fingerprint = ?;",
                   (dev1["id"], "diag_fp_001"))
    count = cursor.fetchone()[0]
    assert count == 1

def test_batch_events_ingestion_with_duplicates(admin_client, paired_devices):
    dev1 = paired_devices["dev1"]
    headers = {"X-Device-Id": dev1["id"], "X-Device-Token": dev1["token"]}

    events = [
        {"event_type": "battery", "payload": {"level": 85}, "client_fingerprint": "bat_85"},
        {"event_type": "battery", "payload": {"level": 80}, "client_fingerprint": "bat_80"},
    ]

    # Ingest batch
    res = admin_client.post(f"/api/v1/devices/{dev1['id']}/events", headers=headers, json={"events": events})
    assert res.status_code == 201
    results = res.json["events"]
    assert len(results) == 2
    assert results[0]["deduplicated"] is False
    assert results[1]["deduplicated"] is False

    # Second batch with one duplicate and one new
    second_batch = [
        {"event_type": "battery", "payload": {"level": 85}, "client_fingerprint": "bat_85"}, # duplicate
        {"event_type": "battery", "payload": {"level": 75}, "client_fingerprint": "bat_75"}, # new
    ]
    res2 = admin_client.post(f"/api/v1/devices/{dev1['id']}/events", headers=headers, json={"events": second_batch})
    assert res2.status_code == 200
    results2 = res2.json["events"]
    assert len(results2) == 2
    assert results2[0]["deduplicated"] is True
    assert results2[1]["deduplicated"] is False

def test_sms_received_event_syncs_to_messages(admin_client, paired_devices):
    dev1 = paired_devices["dev1"]
    headers = {"X-Device-Id": dev1["id"], "X-Device-Token": dev1["token"]}

    event_payload = {
        "event_type": "sms_received",
        "payload": {
            "sender": "+15559999",
            "body": "Verification code is 456789",
            "is_security_code": True,
            "extracted_code": "456789"
        },
        "timestamp": 1731176000000,
        "client_fingerprint": "sms_evt_sync_001"
    }

    res = admin_client.post(f"/api/v1/devices/{dev1['id']}/events", headers=headers, json=event_payload)
    assert res.status_code == 201

    # Verify message was also recorded in messages table
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT id, body, correspondent, extracted_code FROM messages WHERE device_id = ? AND correspondent = ?;",
                   (dev1["id"], "+15559999"))
    row = cursor.fetchone()
    assert row is not None
    assert row["body"] == "Verification code is 456789"
    assert row["extracted_code"] == "456789"

def test_list_events_and_isolation(admin_client, paired_devices):
    dev1 = paired_devices["dev1"]
    dev2 = paired_devices["dev2"]
    headers_dev1 = {"X-Device-Id": dev1["id"], "X-Device-Token": dev1["token"]}
    headers_dev2 = {"X-Device-Id": dev2["id"], "X-Device-Token": dev2["token"]}

    admin_client.post(f"/api/v1/devices/{dev1['id']}/events", headers=headers_dev1, json={
        "event_type": "status", "payload": {"state": "online"}
    })

    # dev1 can list its events
    res1 = admin_client.get(f"/api/v1/devices/{dev1['id']}/events", headers=headers_dev1)
    assert res1.status_code == 200
    assert len(res1.json["events"]) >= 1

    # dev2 cannot access dev1's events (403 Device ID mismatch)
    res_cross = admin_client.get(f"/api/v1/devices/{dev1['id']}/events", headers=headers_dev2)
    assert res_cross.status_code == 403

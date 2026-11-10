"""Contract tests for the actual Connected Android API paths and response fields."""
from app.db import get_db


def headers(device):
    return {"X-Device-Id": device["id"], "X-Device-Token": device["token"]}


def test_android_claim_alias_is_one_time_and_defaults_to_no_remote_send(admin_client):
    token_response = admin_client.post("/api/v1/pairing/start", json={"label_hint": "Pixel"})
    assert token_response.status_code == 201
    token = token_response.json["pairing_token"]
    claim = {"token": token, "label": "Personal Pixel", "phone_number": "+15551231234"}
    response = admin_client.post("/api/device/claim", json=claim)
    assert response.status_code == 201
    assert response.json["device_token"].startswith("dtok_")
    device_id = response.json["device_id"]

    settings = admin_client.get("/api/device/settings", headers={
        "X-Device-Id": device_id, "X-Device-Token": response.json["device_token"]
    })
    assert settings.status_code == 200
    assert settings.json["send_opt_in"] is False
    row = get_db().execute("SELECT metadata FROM devices WHERE id = ?", (device_id,)).fetchone()
    assert "+15551231234" in row["metadata"]
    assert admin_client.post("/api/device/claim", json=claim).status_code == 409


def test_android_jobs_include_sms_and_notifications_but_only_for_one_phone(admin_client, paired_devices):
    phone_a = paired_devices["dev1"]
    phone_b = paired_devices["dev2"]
    with admin_client.session_transaction() as session:
        csrf = session["csrf_token"]

    assert admin_client.post("/send", data={
        "device_id": phone_a["id"], "recipient": "+15551234567", "body": "Message A",
        "csrf_token": csrf
    }).status_code == 302
    assert admin_client.post("/notifications", data={
        "device_id": phone_a["id"], "title": "Ready", "body": "Task completed",
        "csrf_token": csrf
    }).status_code == 302

    response_a = admin_client.get("/api/device/jobs?status=pending", headers=headers(phone_a))
    response_b = admin_client.get("/api/device/jobs?status=pending", headers=headers(phone_b))
    assert response_a.status_code == 200
    assert response_b.status_code == 200
    assert len(response_a.json["jobs"]) == 2
    assert response_b.json["jobs"] == []
    assert {job["kind"] for job in response_a.json["jobs"]} == {"sms", "notification"}

    for job in response_a.json["jobs"]:
        done = admin_client.post("/api/device/ack", headers=headers(phone_a), json={
            "job_id": job["id"], "status": "completed"
        })
        assert done.status_code == 200

    assert admin_client.get("/api/device/jobs", headers=headers(phone_a)).json["jobs"] == []


def test_android_events_settings_and_revoke(admin_client, paired_devices):
    phone = paired_devices["dev2"]
    data = {"events": [{"event_type": "sms_received", "client_fingerprint": "android-one",
                        "payload": {"sender": "+15550001111", "body": "Hello"}}]}

    assert admin_client.post("/api/device/events", json=data).status_code == 401
    upload = admin_client.post("/api/device/events", headers=headers(phone), json=data)
    assert upload.status_code == 201
    assert admin_client.post("/api/device/events", headers=headers(phone), json=data).status_code == 200

    updated = admin_client.patch("/api/device/settings", headers=headers(phone),
                                 json={"send_opt_in": False})
    assert updated.status_code == 200
    assert updated.json["send_opt_in"] is False
    assert admin_client.post("/api/device/revoke", headers=headers(phone), json={}).status_code == 200
    assert admin_client.get("/api/device/jobs", headers=headers(phone)).status_code == 403

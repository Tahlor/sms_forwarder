import pytest
import json
from app.db import get_db, db_transaction

def test_send_opt_in_enforcement_and_toggle(admin_client, paired_devices):
    dev1 = paired_devices["dev1"]
    headers_dev1 = {"X-Device-Id": dev1["id"], "X-Device-Token": dev1["token"]}

    with admin_client.session_transaction() as sess:
        csrf = sess.get("csrf_token")

    # 1. Explicitly set dev1 to opted-out of sending
    with db_transaction() as db:
        db.execute(
            "UPDATE devices SET metadata = ? WHERE id = ?;",
            (json.dumps({"send_opt_in": False}), dev1["id"])
        )

    # 2. Attempt to send SMS via Admin Web UI -> Blocked
    res_web = admin_client.post("/send", data={
        "device_id": dev1["id"],
        "recipient": "+15551111",
        "body": "Should fail due to opt-out",
        "csrf_token": csrf
    }, follow_redirects=True)
    assert res_web.status_code == 200
    assert b"has not opted into outbound SMS sending" in res_web.data

    # 3. Attempt to enqueue job via API -> 403 Forbidden
    res_api = admin_client.post(
        f"/api/v1/devices/{dev1['id']}/jobs",
        headers=headers_dev1,
        json={"recipient": "+15551111", "body": "Should fail API"}
    )
    assert res_api.status_code == 403
    assert "not opted into outbound SMS sending" in res_api.json["error"]

    # Verify no outbound job was created in DB
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT COUNT(*) FROM outbound_jobs WHERE device_id = ?;", (dev1["id"],))
    assert cursor.fetchone()[0] == 0

    # 4. Device opts in via settings endpoint
    res_settings = admin_client.post(
        f"/api/v1/devices/{dev1['id']}/settings",
        headers=headers_dev1,
        json={"send_opt_in": True}
    )
    assert res_settings.status_code == 200
    assert res_settings.json["send_opt_in"] is True

    # 5. Outbound SMS now succeeds via API
    res_api_success = admin_client.post(
        f"/api/v1/devices/{dev1['id']}/jobs",
        headers=headers_dev1,
        json={"recipient": "+15551111", "body": "Authorized outbound SMS"}
    )
    assert res_api_success.status_code == 201
    assert res_api_success.json["status"] == "queued"

    # 6. Outbound SMS now succeeds via Admin Web UI
    res_web_success = admin_client.post("/send", data={
        "device_id": dev1["id"],
        "recipient": "+15552222",
        "body": "Web outbound SMS",
        "csrf_token": csrf
    }, follow_redirects=True)
    assert res_web_success.status_code == 200
    assert b"Outbound SMS queued" in res_web_success.data

import pytest
from app.db import get_db

def test_outbound_job_retry_lifecycle(admin_client, paired_devices):
    dev1 = paired_devices["dev1"]
    headers = {"X-Device-Id": dev1["id"], "X-Device-Token": dev1["token"]}

    # 1. Enqueue job via API
    res = admin_client.post(f"/api/v1/devices/{dev1['id']}/jobs", headers=headers, json={
        "recipient": "+15553333",
        "body": "First attempt"
    })
    assert res.status_code == 201
    job_id = res.json["job_id"]

    # 2. Device acknowledges job as failed
    res_fail = admin_client.post(f"/api/v1/devices/{dev1['id']}/jobs/{job_id}/ack", headers=headers, json={
        "status": "failed",
        "error": "Radio error: NO_SERVICE"
    })
    assert res_fail.status_code == 200

    # 3. Verify job is marked failed
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT status, retry_count FROM outbound_jobs WHERE id = ?;", (job_id,))
    row = cursor.fetchone()
    assert row["status"] == "failed"
    assert row["retry_count"] == 0

    # 4. Device polls pending jobs -> empty
    res_pending = admin_client.get(f"/api/v1/devices/{dev1['id']}/jobs?status=pending", headers=headers)
    assert len(res_pending.json["jobs"]) == 0

    # 5. Device requests retry
    res_retry = admin_client.post(f"/api/v1/devices/{dev1['id']}/jobs/{job_id}/retry", headers=headers)
    assert res_retry.status_code == 200
    assert res_retry.json["status"] == "retried"
    assert res_retry.json["retry_count"] == 1
    assert res_retry.json["job_status"] == "pending"

    # 6. Device polls pending jobs -> job is visible again!
    res_pending_after = admin_client.get(f"/api/v1/devices/{dev1['id']}/jobs?status=pending", headers=headers)
    assert len(res_pending_after.json["jobs"]) == 1
    assert res_pending_after.json["jobs"][0]["id"] == job_id
    assert res_pending_after.json["jobs"][0]["retry_count"] == 1

def test_admin_ui_job_and_notification_retry(admin_client, paired_devices):
    dev2 = paired_devices["dev2"]
    headers2 = {"X-Device-Id": dev2["id"], "X-Device-Token": dev2["token"]}

    with admin_client.session_transaction() as sess:
        csrf = sess.get("csrf_token")

    # 1. Enqueue SMS via Admin UI
    admin_client.post("/send", data={
        "device_id": dev2["id"],
        "recipient": "+15554444",
        "body": "Admin SMS to fail and retry",
        "csrf_token": csrf
    }, follow_redirects=True)

    # Device polls and fails it
    res_poll = admin_client.get(f"/api/v1/devices/{dev2['id']}/jobs?status=pending", headers=headers2)
    job_id = res_poll.json["jobs"][0]["id"]
    admin_client.post(f"/api/v1/devices/{dev2['id']}/jobs/{job_id}/ack", headers=headers2, json={"status": "failed"})

    # 2. Admin UI retries the job
    res_retry_web = admin_client.post(f"/jobs/{job_id}/retry", data={"csrf_token": csrf}, follow_redirects=True)
    assert res_retry_web.status_code == 200
    assert b"re-queued for sending" in res_retry_web.data

    # Verify job is pending with retry_count=1
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT status, retry_count FROM outbound_jobs WHERE id = ?;", (job_id,))
    assert cursor.fetchone()["retry_count"] == 1

    # 3. Notification retry
    admin_client.post("/notifications", data={
        "device_id": dev2["id"],
        "title": "Alert",
        "body": "Check battery",
        "priority": "normal",
        "csrf_token": csrf
    }, follow_redirects=True)

    res_notif = admin_client.get(f"/api/v1/devices/{dev2['id']}/notifications?status=pending", headers=headers2)
    notif_id = res_notif.json["notifications"][0]["id"]
    admin_client.post(f"/api/v1/devices/{dev2['id']}/notifications/{notif_id}/ack", headers=headers2, json={"status": "completed"})

    # Admin retries notification
    res_notif_retry = admin_client.post(f"/notifications/{notif_id}/retry", data={"csrf_token": csrf}, follow_redirects=True)
    assert res_notif_retry.status_code == 200
    assert b"Push notification re-queued" in res_notif_retry.data

    cursor.execute("SELECT status, retry_count FROM notification_jobs WHERE id = ?;", (notif_id,))
    n_row = cursor.fetchone()
    assert n_row["status"] == "pending"
    assert n_row["retry_count"] == 1

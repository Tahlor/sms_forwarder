import pytest
from app.db import get_db

def test_outbound_job_queue_and_ack_lifecycle(admin_client, paired_devices):
    dev1 = paired_devices["dev1"]
    dev2 = paired_devices["dev2"]

    with admin_client.session_transaction() as sess:
        csrf = sess.get("csrf_token")

    # 1. Admin enqueues outbound SMS targeting dev1 explicitly
    res_send = admin_client.post("/send", data={
        "device_id": dev1["id"],
        "recipient": "+15557777",
        "body": "Outbound test message from admin",
        "csrf_token": csrf
    }, follow_redirects=True)
    assert res_send.status_code == 200

    headers_dev1 = {"X-Device-Id": dev1["id"], "X-Device-Token": dev1["token"]}
    headers_dev2 = {"X-Device-Id": dev2["id"], "X-Device-Token": dev2["token"]}

    # 2. dev1 polls jobs -> finds 1 pending job
    res_jobs1 = admin_client.get(f"/api/v1/devices/{dev1['id']}/jobs?status=pending", headers=headers_dev1)
    assert res_jobs1.status_code == 200
    jobs1 = res_jobs1.json["jobs"]
    assert len(jobs1) == 1
    job = jobs1[0]
    assert job["recipient"] == "+15557777"
    assert job["body"] == "Outbound test message from admin"
    assert job["status"] == "pending"
    job_id = job["id"]

    # 3. dev2 polls jobs -> MUST BE EMPTY (Strict isolation: no implicit broadcast)
    res_jobs2 = admin_client.get(f"/api/v1/devices/{dev2['id']}/jobs?status=pending", headers=headers_dev2)
    assert res_jobs2.status_code == 200
    assert len(res_jobs2.json["jobs"]) == 0

    # 4. dev1 acknowledges job as completed
    ack_res = admin_client.post(
        f"/api/v1/devices/{dev1['id']}/jobs/{job_id}/ack",
        headers=headers_dev1,
        json={"status": "completed", "carrier_ack": "DELIVERED_OK"}
    )
    assert ack_res.status_code == 200
    assert ack_res.json["status"] == "acknowledged"

    # 5. dev1 polls pending jobs again -> 0 pending
    res_jobs1_after = admin_client.get(f"/api/v1/devices/{dev1['id']}/jobs?status=pending", headers=headers_dev1)
    assert len(res_jobs1_after.json["jobs"]) == 0

    # 6. Idempotent ack retry
    ack_res_retry = admin_client.post(
        f"/api/v1/devices/{dev1['id']}/jobs/{job_id}/ack",
        headers=headers_dev1,
        json={"status": "completed", "carrier_ack": "DELIVERED_OK"}
    )
    assert ack_res_retry.status_code == 200

def test_notification_queue_and_ack(admin_client, paired_devices):
    dev2 = paired_devices["dev2"]
    headers_dev2 = {"X-Device-Id": dev2["id"], "X-Device-Token": dev2["token"]}

    with admin_client.session_transaction() as sess:
        csrf = sess.get("csrf_token")

    # 1. Enqueue notification
    res_notif = admin_client.post("/notifications", data={
        "device_id": dev2["id"],
        "title": "Battery Low Alert",
        "body": "Please recharge soon",
        "priority": "high",
        "csrf_token": csrf
    }, follow_redirects=True)
    assert res_notif.status_code == 200

    # 2. dev2 polls notifications
    res_poll = admin_client.get(f"/api/v1/devices/{dev2['id']}/notifications?status=pending", headers=headers_dev2)
    assert res_poll.status_code == 200
    notifs = res_poll.json["notifications"]
    assert len(notifs) == 1
    notif = notifs[0]
    assert notif["title"] == "Battery Low Alert"
    assert notif["priority"] == "high"
    notif_id = notif["id"]

    # 3. dev2 acks notification
    res_ack = admin_client.post(
        f"/api/v1/devices/{dev2['id']}/notifications/{notif_id}/ack",
        headers=headers_dev2,
        json={"status": "completed"}
    )
    assert res_ack.status_code == 200
    assert res_ack.json["status"] == "acknowledged"

    # 4. Pending count is now 0
    res_poll_after = admin_client.get(f"/api/v1/devices/{dev2['id']}/notifications?status=pending", headers=headers_dev2)
    assert len(res_poll_after.json["notifications"]) == 0

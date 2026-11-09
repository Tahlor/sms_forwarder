from app.db import db_transaction


def test_outbound_sms_is_denied_by_default_without_explicit_opt_in(admin_client, paired_devices):
    phone = paired_devices["dev1"]
    with db_transaction() as db:
        db.execute("UPDATE devices SET metadata = '{}' WHERE id = ?", (phone["id"],))

    with admin_client.session_transaction() as sess:
        csrf = sess["csrf_token"]

    response = admin_client.post("/send", data={
        "device_id": phone["id"],
        "recipient": "+15550001111",
        "body": "Must not be sent without phone consent",
        "csrf_token": csrf,
    }, follow_redirects=True)
    assert response.status_code == 200
    assert b"not opted into outbound SMS" in response.data

    headers = {"X-Device-Id": phone["id"], "X-Device-Token": phone["token"]}
    result = admin_client.post(f"/api/v1/devices/{phone['id']}/jobs", headers=headers, json={
        "recipient": "+15550001111",
        "body": "Also must be refused",
    })
    assert result.status_code == 403
    jobs = admin_client.get(f"/api/v1/devices/{phone['id']}/jobs", headers=headers)
    assert jobs.status_code == 200
    assert jobs.json["jobs"] == []

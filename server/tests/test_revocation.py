import pytest

def test_admin_revocation_blocks_device(admin_client, paired_devices):
    dev1 = paired_devices["dev1"]
    headers = {"X-Device-Id": dev1["id"], "X-Device-Token": dev1["token"]}

    # Device works normally prior to revocation
    res = admin_client.get(f"/api/v1/devices/{dev1['id']}/jobs", headers=headers)
    assert res.status_code == 200

    # Admin revokes device
    with admin_client.session_transaction() as sess:
        csrf = sess.get("csrf_token")
    res_revoke = admin_client.post(f"/devices/{dev1['id']}/revoke", data={"csrf_token": csrf}, follow_redirects=True)
    assert res_revoke.status_code == 200
    assert b"has been revoked" in res_revoke.data

    # Subsequent requests from dev1 are denied with 403 Forbidden
    res_blocked = admin_client.get(f"/api/v1/devices/{dev1['id']}/jobs", headers=headers)
    assert res_blocked.status_code == 403
    assert "revoked" in res_blocked.json["error"]

    # All device endpoints reject revoked device
    assert admin_client.post(f"/api/v1/devices/{dev1['id']}/messages", headers=headers, json={"sender": "+15550000", "body": "X"}).status_code == 403
    assert admin_client.post(f"/api/v1/devices/{dev1['id']}/events", headers=headers, json={"event_type": "status"}).status_code == 403
    assert admin_client.get(f"/api/v1/devices/{dev1['id']}/notifications", headers=headers).status_code == 403
    assert admin_client.get(f"/api/v1/devices/{dev1['id']}/conversations", headers=headers).status_code == 403
    assert admin_client.get(f"/api/v1/devices/{dev1['id']}/settings", headers=headers).status_code == 403

def test_device_self_revocation(admin_client, paired_devices):
    dev2 = paired_devices["dev2"]
    headers = {"X-Device-Id": dev2["id"], "X-Device-Token": dev2["token"]}

    # Device self-revokes on unpair
    res_self = admin_client.post(f"/api/v1/devices/{dev2['id']}/revoke", headers=headers)
    assert res_self.status_code == 200
    assert res_self.json["status"] == "revoked"

    # Subsequent requests immediately fail
    res_after = admin_client.get(f"/api/v1/devices/{dev2['id']}/jobs", headers=headers)
    assert res_after.status_code == 403
    assert "revoked" in res_after.json["error"]

def test_admin_api_revoke(admin_client, paired_devices):
    dev1 = paired_devices["dev1"]
    headers1 = {"X-Device-Id": dev1["id"], "X-Device-Token": dev1["token"]}

    res = admin_client.post(f"/api/v1/devices/{dev1['id']}/admin-revoke")
    assert res.status_code == 200
    assert res.json["status"] == "revoked"

    # Verify device is now blocked
    res_blocked = admin_client.get(f"/api/v1/devices/{dev1['id']}/jobs", headers=headers1)
    assert res_blocked.status_code == 403

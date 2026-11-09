import pytest

def test_two_phones_same_correspondent_isolation(admin_client, paired_devices):
    dev1 = paired_devices["dev1"]
    dev2 = paired_devices["dev2"]
    shared_contact = "+15551234567"

    headers_dev1 = {
        "X-Device-Id": dev1["id"],
        "X-Device-Token": dev1["token"]
    }
    headers_dev2 = {
        "X-Device-Id": dev2["id"],
        "X-Device-Token": dev2["token"]
    }

    # Phone 1 receives SMS from +15551234567
    res1 = admin_client.post(
        f"/api/v1/devices/{dev1['id']}/messages",
        headers=headers_dev1,
        json={
            "sender": shared_contact,
            "body": "Hello Phone 1 from contact",
            "timestamp": 1731170000000
        }
    )
    assert res1.status_code == 201

    # Phone 2 receives SMS from SAME contact +15551234567
    res2 = admin_client.post(
        f"/api/v1/devices/{dev2['id']}/messages",
        headers=headers_dev2,
        json={
            "sender": shared_contact,
            "body": "Hello Phone 2 from same contact",
            "timestamp": 1731170005000
        }
    )
    assert res2.status_code == 201

    # In Dashboard:
    # When filtering by dev1, we must see thread for dev1 and NOT dev2's message
    res_dash_dev1 = admin_client.get(f"/?device_id={dev1['id']}&view_device={dev1['id']}&view_corr={shared_contact}")
    assert res_dash_dev1.status_code == 200
    assert b"Hello Phone 1 from contact" in res_dash_dev1.data
    assert b"Hello Phone 2 from same contact" not in res_dash_dev1.data

    # When filtering by dev2, we must see thread for dev2 and NOT dev1's message
    res_dash_dev2 = admin_client.get(f"/?device_id={dev2['id']}&view_device={dev2['id']}&view_corr={shared_contact}")
    assert res_dash_dev2.status_code == 200
    assert b"Hello Phone 2 from same contact" in res_dash_dev2.data
    assert b"Hello Phone 1 from contact" not in res_dash_dev2.data

    # When viewing "All phones", both threads appear as distinct entries with their device labels
    res_dash_all = admin_client.get("/?device_id=all")
    assert res_dash_all.status_code == 200
    assert b"Pixel 8 (Work)" in res_dash_all.data
    assert b"Galaxy S23 (Personal)" in res_dash_all.data

def test_cross_device_access_forbidden(admin_client, paired_devices):
    dev1 = paired_devices["dev1"]
    dev2 = paired_devices["dev2"]

    headers_dev1 = {
        "X-Device-Id": dev1["id"],
        "X-Device-Token": dev1["token"]
    }

    # dev1 attempts to access dev2's job queue using dev1's credentials -> 403 Forbidden
    res = admin_client.get(f"/api/v1/devices/{dev2['id']}/jobs", headers=headers_dev1)
    assert res.status_code == 403
    assert "Device ID mismatch" in res.json["error"]

    # dev1 attempts to upload message into dev2's inbox -> 403 Forbidden
    res = admin_client.post(
        f"/api/v1/devices/{dev2['id']}/messages",
        headers=headers_dev1,
        json={"sender": "+15559999", "body": "Spoofed"}
    )
    assert res.status_code == 403

    # dev1 attempts to access dev2's notifications -> 403 Forbidden
    res = admin_client.get(f"/api/v1/devices/{dev2['id']}/notifications", headers=headers_dev1)
    assert res.status_code == 403

    # dev1 attempts to access dev2's conversations -> 403 Forbidden
    res = admin_client.get(f"/api/v1/devices/{dev2['id']}/conversations", headers=headers_dev1)
    assert res.status_code == 403

    # dev1 attempts to access dev2's events -> 403 Forbidden
    res = admin_client.get(f"/api/v1/devices/{dev2['id']}/events", headers=headers_dev1)
    assert res.status_code == 403

    # dev1 attempts to update dev2's settings -> 403 Forbidden
    res = admin_client.post(f"/api/v1/devices/{dev2['id']}/settings", headers=headers_dev1, json={"label": "Hacked"})
    assert res.status_code == 403

    # dev1 attempts to trigger self-revoke on dev2 -> 403 Forbidden
    res = admin_client.post(f"/api/v1/devices/{dev2['id']}/revoke", headers=headers_dev1)
    assert res.status_code == 403

    # dev1 attempts to ack dev2's job -> 403 Forbidden
    res = admin_client.post(f"/api/v1/devices/{dev2['id']}/jobs/job_123/ack", headers=headers_dev1, json={"status": "completed"})
    assert res.status_code == 403

    # dev1 attempts to retry dev2's job -> 403 Forbidden
    res = admin_client.post(f"/api/v1/devices/{dev2['id']}/jobs/job_123/retry", headers=headers_dev1)
    assert res.status_code == 403

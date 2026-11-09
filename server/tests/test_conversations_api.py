import pytest

def test_device_conversations_and_message_queries(admin_client, paired_devices):
    dev1 = paired_devices["dev1"]
    headers = {"X-Device-Id": dev1["id"], "X-Device-Token": dev1["token"]}

    # Upload several messages
    admin_client.post(f"/api/v1/devices/{dev1['id']}/messages", headers=headers, json={
        "sender": "+15551001",
        "body": "Message 1 from Contact A",
        "timestamp": 1731170001000
    })
    admin_client.post(f"/api/v1/devices/{dev1['id']}/messages", headers=headers, json={
        "sender": "+15551001",
        "body": "Message 2 from Contact A",
        "timestamp": 1731170002000
    })
    admin_client.post(f"/api/v1/devices/{dev1['id']}/messages", headers=headers, json={
        "sender": "+15552002",
        "body": "Message 1 from Contact B",
        "timestamp": 1731170003000
    })

    # 1. Device lists conversations
    res_convs = admin_client.get(f"/api/v1/devices/{dev1['id']}/conversations", headers=headers)
    assert res_convs.status_code == 200
    convs = res_convs.json["conversations"]
    assert len(convs) == 2
    # Verify Contact A has 2 messages
    contact_a = next(c for c in convs if c["correspondent"] == "+15551001")
    assert contact_a["msg_count"] == 2
    assert contact_a["last_body"] == "Message 2 from Contact A"

    # 2. Device lists messages filtered by correspondent
    res_msgs = admin_client.get(f"/api/v1/devices/{dev1['id']}/messages?correspondent=%2B15551001", headers=headers)
    assert res_msgs.status_code == 200
    msgs = res_msgs.json["messages"]
    assert len(msgs) == 2
    assert msgs[0]["body"] == "Message 1 from Contact A"
    assert msgs[1]["body"] == "Message 2 from Contact A"

    # 3. Device fetches thread messages
    res_thread = admin_client.get(f"/api/v1/devices/{dev1['id']}/conversations/%2B15551001/messages", headers=headers)
    assert res_thread.status_code == 200
    assert len(res_thread.json["messages"]) == 2

def test_admin_conversations_api(admin_client, paired_devices):
    dev1 = paired_devices["dev1"]
    dev2 = paired_devices["dev2"]
    headers1 = {"X-Device-Id": dev1["id"], "X-Device-Token": dev1["token"]}
    headers2 = {"X-Device-Id": dev2["id"], "X-Device-Token": dev2["token"]}

    admin_client.post(f"/api/v1/devices/{dev1['id']}/messages", headers=headers1, json={
        "sender": "+15557777",
        "body": "Message to dev1",
        "timestamp": 1731170010000
    })
    admin_client.post(f"/api/v1/devices/{dev2['id']}/messages", headers=headers2, json={
        "sender": "+15557777",
        "body": "Message to dev2 (same correspondent)",
        "timestamp": 1731170020000
    })

    # Admin lists all conversations
    res = admin_client.get("/api/v1/conversations")
    assert res.status_code == 200
    convs = res.json["conversations"]
    assert len(convs) >= 2
    # Both dev1 and dev2 appear as separate conversation entries for the same correspondent
    dev1_entry = next(c for c in convs if c["device_id"] == dev1["id"] and c["correspondent"] == "+15557777")
    dev2_entry = next(c for c in convs if c["device_id"] == dev2["id"] and c["correspondent"] == "+15557777")
    assert dev1_entry is not None
    assert dev2_entry is not None
    assert dev1_entry["last_body"] == "Message to dev1"
    assert dev2_entry["last_body"] == "Message to dev2 (same correspondent)"

    # Admin thread view
    res_thread = admin_client.get(f"/api/v1/conversations/{dev1['id']}/%2B15557777")
    assert res_thread.status_code == 200
    assert len(res_thread.json["messages"]) == 1
    assert res_thread.json["messages"][0]["body"] == "Message to dev1"

def test_conversations_cross_device_and_anon_forbidden(client, paired_devices):
    dev1 = paired_devices["dev1"]
    dev2 = paired_devices["dev2"]
    headers1 = {"X-Device-Id": dev1["id"], "X-Device-Token": dev1["token"]}

    # Anonymous access to admin conversations -> 401
    res_anon = client.get("/api/v1/conversations")
    assert res_anon.status_code == 401

    # dev1 credentials attempting to read dev2's conversations -> 403 Forbidden
    res_cross = client.get(f"/api/v1/devices/{dev2['id']}/conversations", headers=headers1)
    assert res_cross.status_code == 403
    assert "Device ID mismatch" in res_cross.json["error"]

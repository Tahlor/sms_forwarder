import pytest

def test_xss_prevention_in_html_rendering(admin_client, paired_devices):
    dev1 = paired_devices["dev1"]
    headers = {"X-Device-Id": dev1["id"], "X-Device-Token": dev1["token"]}

    malicious_body = "<script>alert('xss-body')</script>"
    malicious_sender = "+1555<img src=x onerror=alert('xss-sender')>"

    # Upload message with malicious payload
    res_upload = admin_client.post(f"/api/v1/devices/{dev1['id']}/messages", headers=headers, json={
        "sender": malicious_sender,
        "body": malicious_body,
        "timestamp": 1731174000000
    })
    assert res_upload.status_code == 201

    # Render dashboard containing thread and message
    res_dash = admin_client.get(f"/?device_id={dev1['id']}&view_device={dev1['id']}&view_corr={malicious_sender}")
    assert res_dash.status_code == 200
    html = res_dash.data.decode("utf-8")

    # Invariants: raw unescaped script and img tags must NEVER appear in the HTML
    assert "<script>alert('xss-body')</script>" not in html
    assert "<img src=x onerror=alert('xss-sender')>" not in html

    # Instead, HTML escaped entities must be present
    assert "&lt;script&gt;alert(&#39;xss-body&#39;)&lt;/script&gt;" in html or "&lt;script&gt;alert('xss-body')&lt;/script&gt;" in html
    assert "&lt;img src=x onerror=alert(&#39;xss-sender&#39;)&gt;" in html or "&lt;img src=x onerror=alert('xss-sender')&gt;" in html

def test_security_headers_present(client):
    res = client.get("/health")
    assert res.status_code == 200
    assert res.headers.get("X-Frame-Options") == "DENY"
    assert res.headers.get("X-Content-Type-Options") == "nosniff"
    assert "default-src 'self'" in res.headers.get("Content-Security-Policy", "")
    assert res.headers.get("Referrer-Policy") == "strict-origin-when-cross-origin"

import jinja2
from flask import url_for, get_flashed_messages

BASE_TEMPLATE = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>{% block title %}SMS Bridge Admin{% endblock %}</title>
  <style>
    :root {
      --bg: #0f172a;
      --card: #1e293b;
      --card-border: #334155;
      --text: #f8fafc;
      --text-muted: #94a3b8;
      --primary: #3b82f6;
      --primary-hover: #2563eb;
      --success: #10b981;
      --danger: #ef4444;
      --warning: #f59e0b;
      --badge-bg: #334155;
    }
    @media (prefers-color-scheme: light) {
      :root {
        --bg: #f8fafc;
        --card: #ffffff;
        --card-border: #e2e8f0;
        --text: #0f172a;
        --text-muted: #64748b;
        --primary: #2563eb;
        --primary-hover: #1d4ed8;
        --badge-bg: #e2e8f0;
      }
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      background: var(--bg);
      color: var(--text);
      line-height: 1.5;
      padding: 1.5rem;
    }
    .container { max-width: 1200px; margin: 0 auto; }
    header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-bottom: 1.5rem;
      border-bottom: 1px solid var(--card-border);
      margin-bottom: 1.5rem;
    }
    .header-title { font-size: 1.5rem; font-weight: 700; display: flex; align-items: center; gap: 0.5rem; }
    .badge {
      font-size: 0.75rem;
      font-weight: 600;
      padding: 0.2rem 0.5rem;
      border-radius: 9999px;
      background: var(--badge-bg);
      color: var(--text);
    }
    .badge-success { background: #065f46; color: #a7f3d0; }
    .badge-danger { background: #7f1d1d; color: #fecaca; }
    .badge-sec { background: #4c1d95; color: #ddd6fe; }
    .nav-actions { display: flex; gap: 0.75rem; align-items: center; }
    button, .btn {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 1rem;
      font-size: 0.875rem;
      font-weight: 600;
      border-radius: 0.375rem;
      border: none;
      cursor: pointer;
      text-decoration: none;
      transition: background 0.15s ease;
    }
    .btn-primary { background: var(--primary); color: #ffffff; }
    .btn-primary:hover { background: var(--primary-hover); }
    .btn-danger { background: var(--danger); color: #ffffff; }
    .btn-secondary { background: var(--badge-bg); color: var(--text); }
    .btn-sm { padding: 0.25rem 0.5rem; font-size: 0.75rem; }
    .grid { display: grid; grid-template-columns: 1fr 2fr; gap: 1.5rem; }
    @media (max-width: 900px) { .grid { grid-template-columns: 1fr; } }
    .card {
      background: var(--card);
      border: 1px solid var(--card-border);
      border-radius: 0.5rem;
      padding: 1.25rem;
      margin-bottom: 1.5rem;
    }
    .card-title {
      font-size: 1.125rem;
      font-weight: 600;
      margin-bottom: 1rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .form-group { margin-bottom: 1rem; }
    label { display: block; font-size: 0.875rem; font-weight: 500; margin-bottom: 0.375rem; color: var(--text-muted); }
    input, select, textarea {
      width: 100%;
      padding: 0.5rem 0.75rem;
      border-radius: 0.375rem;
      border: 1px solid var(--card-border);
      background: var(--bg);
      color: var(--text);
      font-size: 0.875rem;
      font-family: inherit;
    }
    input:focus, select:focus, textarea:focus {
      outline: 2px solid var(--primary);
    }
    .flash-messages { margin-bottom: 1rem; }
    .alert {
      padding: 0.75rem 1rem;
      border-radius: 0.375rem;
      margin-bottom: 0.5rem;
      font-size: 0.875rem;
    }
    .alert-success { background: #065f46; color: #a7f3d0; border: 1px solid #047857; }
    .alert-danger { background: #7f1d1d; color: #fecaca; border: 1px solid #b91c1c; }
    .thread-item {
      padding: 0.75rem;
      border-bottom: 1px solid var(--card-border);
      text-decoration: none;
      display: block;
      color: inherit;
      transition: background 0.15s ease;
      border-radius: 0.25rem;
    }
    .thread-item:hover, .thread-item.active { background: var(--badge-bg); }
    .thread-meta { display: flex; justify-content: space-between; font-size: 0.75rem; color: var(--text-muted); margin-bottom: 0.25rem; }
    .thread-sender { font-weight: 600; font-size: 0.95rem; }
    .thread-preview {
      font-size: 0.85rem;
      color: var(--text-muted);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .msg-bubble {
      max-width: 80%;
      padding: 0.75rem 1rem;
      border-radius: 0.75rem;
      margin-bottom: 0.75rem;
      font-size: 0.9rem;
      word-break: break-word;
    }
    .msg-inbound { background: var(--card-border); align-self: flex-start; }
    .msg-outbound { background: var(--primary); color: #ffffff; align-self: flex-end; margin-left: auto; }
    .msg-time { font-size: 0.7rem; opacity: 0.75; margin-top: 0.25rem; text-align: right; }
    .messages-container {
      display: flex;
      flex-direction: column;
      max-height: 480px;
      overflow-y: auto;
      padding: 1rem 0;
      border-bottom: 1px solid var(--card-border);
      margin-bottom: 1rem;
    }
    table { width: 100%; border-collapse: collapse; font-size: 0.875rem; }
    th, td { text-align: left; padding: 0.6rem; border-bottom: 1px solid var(--card-border); }
    th { color: var(--text-muted); font-weight: 500; }
    .modal {
      display: none;
      position: fixed;
      top: 0; left: 0; right: 0; bottom: 0;
      background: rgba(0, 0, 0, 0.75);
      align-items: center;
      justify-content: center;
      z-index: 1000;
    }
    .modal.show { display: flex; }
    .modal-content {
      background: var(--card);
      border: 1px solid var(--card-border);
      border-radius: 0.5rem;
      max-width: 500px;
      width: 90%;
      padding: 1.5rem;
      text-align: center;
    }
    .qr-box { margin: 1rem auto; max-width: 260px; padding: 0.5rem; background: #ffffff; border-radius: 0.5rem; }
    .qr-box svg { width: 100%; height: auto; display: block; }
    .code-block {
      background: var(--bg);
      border: 1px solid var(--card-border);
      border-radius: 0.25rem;
      padding: 0.5rem;
      font-family: monospace;
      font-size: 0.8rem;
      word-break: break-all;
      margin-top: 0.5rem;
      user-select: all;
    }
  </style>
</head>
<body>
  <div class="container">
    {% with messages = get_flashed_messages(with_categories=true) %}
      {% if messages %}
        <div class="flash-messages">
          {% for category, message in messages %}
            <div class="alert alert-{{ category }}">{{ message }}</div>
          {% endfor %}
        </div>
      {% endif %}
    {% endwith %}

    {% block content %}{% endblock %}
  </div>
</body>
</html>
"""

LOGIN_TEMPLATE = """{% extends "base.html" %}
{% block title %}SMS Bridge Login{% endblock %}
{% block content %}
<div style="max-width: 400px; margin: 4rem auto;">
  <div class="card">
    <div class="card-title">🔐 SMS Bridge Admin</div>
    <form method="POST" action="{{ url_for('web_admin.login') }}">
      <input type="hidden" name="csrf_token" value="{{ csrf_token() }}">
      <div class="form-group">
        <label for="username">Username</label>
        <input type="text" id="username" name="username" required autofocus>
      </div>
      <div class="form-group">
        <label for="password">Password</label>
        <input type="password" id="password" name="password" required>
      </div>
      <button type="submit" class="btn btn-primary" style="width: 100%;">Log In</button>
    </form>
  </div>
</div>
{% endblock %}
"""

DASHBOARD_TEMPLATE = """{% extends "base.html" %}
{% block title %}SMS Bridge Dashboard{% endblock %}
{% block content %}
<header>
  <div class="header-title">
    📱 SMS Bridge
    <span class="badge badge-success">{{ active_devices|length }} online</span>
  </div>
  <div class="nav-actions">
    <button class="btn btn-primary" onclick="openPairingModal()">+ Pair New Phone</button>
    <form method="POST" action="{{ url_for('web_admin.logout') }}" style="display:inline;">
      <input type="hidden" name="csrf_token" value="{{ csrf_token() }}">
      <button type="submit" class="btn btn-secondary btn-sm">Log Out</button>
    </form>
  </div>
</header>

<!-- Filter Selector -->
<div class="card" style="padding: 0.75rem 1.25rem;">
  <form method="GET" action="{{ url_for('web_admin.dashboard') }}" style="display: flex; gap: 1rem; align-items: center; flex-wrap: wrap;">
    <label style="margin: 0; font-weight: 600;">Filter Phone:</label>
    <select name="device_id" onchange="this.form.submit()" style="width: auto; min-width: 220px;">
      <option value="all" {% if selected_device_id == 'all' %}selected{% endif %}>📱 All Phones ({{ devices|length }})</option>
      {% for d in devices %}
        <option value="{{ d.id }}" {% if selected_device_id == d.id %}selected{% endif %}>
          {{ d.label }} ({{ d.id[:10] }}...){% if d.revoked_at %} [REVOKED]{% endif %}
        </option>
      {% endfor %}
    </select>
  </form>
</div>

<div class="grid">
  <!-- Left Column: Devices and Actions -->
  <div>
    <!-- Devices Table -->
    <div class="card">
      <div class="card-title">Connected Phones</div>
      {% if devices %}
        <table>
          <thead>
            <tr><th>Label</th><th>Last Seen</th><th>Action</th></tr>
          </thead>
          <tbody>
            {% for d in devices %}
              <tr>
                <td>
                  <strong>{{ d.label }}</strong><br>
                  <span style="font-size: 0.7rem; color: var(--text-muted);">{{ d.id }}</span>
                  {% if d.revoked_at %}
                    <span class="badge badge-danger">Revoked</span>
                  {% else %}
                    <span class="badge badge-success">Active</span>
                    {% if d.send_opt_in %}
                      <span class="badge badge-success">Send: Opt-In</span>
                    {% else %}
                      <span class="badge badge-danger">Send: Opt-Out</span>
                    {% endif %}
                  {% endif %}
                </td>
                <td style="font-size: 0.75rem; color: var(--text-muted);">
                  {{ d.last_seen_at or 'Never' }}
                </td>
                <td>
                  {% if not d.revoked_at %}
                    <form method="POST" action="{{ url_for('web_admin.revoke_device', device_id=d.id) }}" onsubmit="return confirm('Revoke this phone access?');">
                      <input type="hidden" name="csrf_token" value="{{ csrf_token() }}">
                      <button type="submit" class="btn btn-danger btn-sm">Revoke</button>
                    </form>
                  {% else %}
                    <span style="font-size: 0.75rem; color: var(--text-muted);">Revoked</span>
                  {% endif %}
                </td>
              </tr>
            {% endfor %}
          </tbody>
        </table>
      {% else %}
        <p style="color: var(--text-muted); font-size: 0.875rem;">No phones paired yet. Click <strong>+ Pair New Phone</strong> to pair one.</p>
      {% endif %}
    </div>

    <!-- Send Outbound SMS Form -->
    <div class="card">
      <div class="card-title">Send Outbound SMS</div>
      <form method="POST" action="{{ url_for('web_admin.send_sms') }}">
        <input type="hidden" name="csrf_token" value="{{ csrf_token() }}">
        <div class="form-group">
          <label for="send_device_id">Source Phone (Explicit Selection Required)</label>
          <select id="send_device_id" name="device_id" required>
            <option value="" disabled {% if not selected_device_id or selected_device_id == 'all' %}selected{% endif %}>-- Select Sending Phone --</option>
            {% for d in active_devices %}
              <option value="{{ d.id }}" {% if selected_device_id == d.id %}selected{% endif %}>{{ d.label }} ({{ d.id[:10] }}...)</option>
            {% endfor %}
          </select>
          <small style="color: var(--text-muted); font-size: 0.75rem;">SMS will be dispatched exclusively through this selected Android phone. No broadcast.</small>
        </div>
        <div class="form-group">
          <label for="recipient">Recipient Phone Number</label>
          <input type="text" id="recipient" name="recipient" placeholder="+15551234" required value="{{ prefill_recipient or '' }}">
        </div>
        <div class="form-group">
          <label for="body">Message Body</label>
          <textarea id="body" name="body" rows="3" placeholder="Type SMS message..." required></textarea>
        </div>
        <button type="submit" class="btn btn-primary" style="width: 100%;">Enqueue Send Job</button>
      </form>
    </div>

    <!-- Enqueue Notification Form -->
    <div class="card">
      <div class="card-title">Push Notification to Phone</div>
      <form method="POST" action="{{ url_for('web_admin.send_notification') }}">
        <input type="hidden" name="csrf_token" value="{{ csrf_token() }}">
        <div class="form-group">
          <label for="notif_device_id">Target Phone</label>
          <select id="notif_device_id" name="device_id" required>
            <option value="" disabled selected>-- Select Phone --</option>
            {% for d in active_devices %}
              <option value="{{ d.id }}">{{ d.label }}</option>
            {% endfor %}
          </select>
        </div>
        <div class="form-group">
          <label for="notif_title">Notification Title</label>
          <input type="text" id="notif_title" name="title" placeholder="Server Alert" required>
        </div>
        <div class="form-group">
          <label for="notif_body">Notification Body</label>
          <textarea id="notif_body" name="body" rows="2" placeholder="Message content..." required></textarea>
        </div>
        <div class="form-group">
          <label for="notif_priority">Priority</label>
          <select id="notif_priority" name="priority">
            <option value="normal" selected>Normal</option>
            <option value="high">High</option>
            <option value="low">Low</option>
          </select>
        </div>
        <button type="submit" class="btn btn-secondary" style="width: 100%;">Enqueue Push Notification</button>
      </form>
    </div>
  </div>

  <!-- Right Column: Threads and Messages -->
  <div>
    {% if active_thread %}
      <!-- Thread Detail View -->
      <div class="card">
        <div class="card-title">
          <div>
            <span>Conversation with <strong>{{ active_thread.correspondent }}</strong></span>
            <span class="badge badge-success" style="margin-left: 0.5rem;">Via {{ active_thread.device_label }}</span>
          </div>
          <a href="{{ url_for('web_admin.dashboard', device_id=selected_device_id) }}" class="btn btn-secondary btn-sm">Close Thread</a>
        </div>

        <div class="messages-container">
          {% for m in thread_messages %}
            <div class="msg-bubble {% if m.direction == 'inbound' %}msg-inbound{% else %}msg-outbound{% endif %}">
              <div>{{ m.body }}</div>
              {% if m.is_security_code %}
                <div style="margin-top: 0.25rem;">
                  <span class="badge badge-sec">🔑 Security Code: {{ m.extracted_code or 'Detected' }}</span>
                </div>
              {% endif %}
              <div class="msg-time">{{ m.formatted_time }} • {{ m.direction }}</div>
            </div>
          {% endfor %}
        </div>

        <!-- Quick Reply Box -->
        <form method="POST" action="{{ url_for('web_admin.send_sms') }}">
          <input type="hidden" name="csrf_token" value="{{ csrf_token() }}">
          <input type="hidden" name="device_id" value="{{ active_thread.device_id }}">
          <input type="hidden" name="recipient" value="{{ active_thread.correspondent }}">
          <div style="display: flex; gap: 0.5rem;">
            <input type="text" name="body" placeholder="Reply to {{ active_thread.correspondent }} via {{ active_thread.device_label }}..." required style="flex: 1;">
            <button type="submit" class="btn btn-primary">Send</button>
          </div>
        </form>
      </div>
    {% endif %}

    <!-- Thread List -->
    <div class="card">
      <div class="card-title">
        Conversations (Keyed by Phone & Correspondent)
        <span class="badge">{{ threads|length }} threads</span>
      </div>
      {% if threads %}
        <div>
          {% for t in threads %}
            <a class="thread-item {% if active_thread and active_thread.device_id == t.device_id and active_thread.correspondent == t.correspondent %}active{% endif %}"
               href="{{ url_for('web_admin.dashboard', device_id=selected_device_id, view_device=t.device_id, view_corr=t.correspondent) }}">
              <div class="thread-meta">
                <span>📱 <strong>{{ t.device_label }}</strong> &bull; {{ t.correspondent }}</span>
                <span>{{ t.formatted_time }}</span>
              </div>
              <div class="thread-preview">
                {% if t.last_is_sec %}🔑 {% endif %}{{ t.last_body }}
              </div>
            </a>
          {% endfor %}
        </div>
      {% else %}
        <p style="color: var(--text-muted); font-size: 0.875rem;">No conversation threads found for this filter.</p>
      {% endif %}
    </div>
  </div>
</div>

<!-- Outbound Jobs & Notification Queue Card -->
<div class="card">
  <div class="card-title">
    Outbound SMS Queue & Notifications
    <span class="badge">{{ recent_jobs|length }} SMS / {{ recent_notifications|length }} Notif</span>
  </div>

  <div style="margin-bottom: 1.5rem;">
    <h4 style="font-size: 0.95rem; margin-bottom: 0.5rem; color: var(--text-muted);">Outbound SMS Dispatch Queue</h4>
    {% if recent_jobs %}
      <table>
        <thead>
          <tr><th>Phone</th><th>Recipient</th><th>Message</th><th>Status</th><th>Updated</th><th>Action</th></tr>
        </thead>
        <tbody>
          {% for j in recent_jobs %}
            <tr>
              <td><strong>{{ j.device_label }}</strong></td>
              <td>{{ j.recipient }}</td>
              <td style="max-width: 250px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">{{ j.body }}</td>
              <td>
                {% if j.status == 'completed' %}
                  <span class="badge badge-success">Completed</span>
                {% elif j.status == 'failed' %}
                  <span class="badge badge-danger">Failed{% if j.retry_count %} ({{ j.retry_count }}x){% endif %}</span>
                {% else %}
                  <span class="badge">{{ j.status }}{% if j.retry_count %} ({{ j.retry_count }}x){% endif %}</span>
                {% endif %}
              </td>
              <td style="font-size: 0.75rem; color: var(--text-muted);">{{ j.updated_at }}</td>
              <td>
                {% if j.status in ('failed', 'completed') %}
                  <form method="POST" action="{{ url_for('web_admin.retry_job', job_id=j.id) }}" style="display:inline;">
                    <input type="hidden" name="csrf_token" value="{{ csrf_token() }}">
                    <button type="submit" class="btn btn-secondary btn-sm">Retry Send</button>
                  </form>
                {% else %}
                  <span style="font-size: 0.75rem; color: var(--text-muted);">&bull; Queued</span>
                {% endif %}
              </td>
            </tr>
          {% endfor %}
        </tbody>
      </table>
    {% else %}
      <p style="color: var(--text-muted); font-size: 0.85rem;">No outbound SMS jobs found.</p>
    {% endif %}
  </div>

  <div>
    <h4 style="font-size: 0.95rem; margin-bottom: 0.5rem; color: var(--text-muted);">Push Notification Jobs</h4>
    {% if recent_notifications %}
      <table>
        <thead>
          <tr><th>Phone</th><th>Title</th><th>Body</th><th>Priority</th><th>Status</th><th>Action</th></tr>
        </thead>
        <tbody>
          {% for n in recent_notifications %}
            <tr>
              <td><strong>{{ n.device_label }}</strong></td>
              <td>{{ n.title }}</td>
              <td style="max-width: 250px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">{{ n.body }}</td>
              <td><span class="badge">{{ n.priority }}</span></td>
              <td>
                {% if n.status == 'completed' %}
                  <span class="badge badge-success">Completed</span>
                {% else %}
                  <span class="badge">{{ n.status }}{% if n.retry_count %} ({{ n.retry_count }}x){% endif %}</span>
                {% endif %}
              </td>
              <td>
                <form method="POST" action="{{ url_for('web_admin.retry_notification', notif_id=n.id) }}" style="display:inline;">
                  <input type="hidden" name="csrf_token" value="{{ csrf_token() }}">
                  <button type="submit" class="btn btn-secondary btn-sm">Re-push</button>
                </form>
              </td>
            </tr>
          {% endfor %}
        </tbody>
      </table>
    {% else %}
      <p style="color: var(--text-muted); font-size: 0.85rem;">No notification jobs found.</p>
    {% endif %}
  </div>
</div>

<!-- Pairing Modal -->
<div id="pairingModal" class="modal">
  <div class="modal-content">
    <div class="card-title" style="justify-content: center;">Pair New Android Phone</div>
    <p style="font-size: 0.85rem; color: var(--text-muted);">Scan this QR code with the Connected SMS Bridge Android app or copy the manual code below. Valid for 10 minutes (one-time use).</p>
    
    <div id="pairingQrLoading" style="padding: 2rem;">Generating pairing token...</div>
    <div id="pairingQrContainer" style="display: none;">
      <div class="qr-box" id="qrSvgWrapper"></div>
      <div style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.5rem;">Manual Pairing Payload:</div>
      <div class="code-block" id="pairingPayload"></div>
      <div style="margin-top: 0.5rem; font-size: 0.75rem; color: var(--warning);">⚠️ Token expires in: <span id="pairingCountdown">600</span>s</div>
    </div>

    <div style="margin-top: 1.5rem; display: flex; gap: 0.5rem; justify-content: center;">
      <button class="btn btn-secondary" onclick="closePairingModal()">Close</button>
    </div>
  </div>
</div>

<script>
  let countdownTimer = null;
  function openPairingModal() {
    const modal = document.getElementById('pairingModal');
    modal.classList.add('show');
    document.getElementById('pairingQrLoading').style.display = 'block';
    document.getElementById('pairingQrContainer').style.display = 'none';

    fetch('/api/v1/pairing/start', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-CSRF-Token': '{{ csrf_token() }}'
      },
      body: JSON.stringify({ label_hint: 'Android Phone' })
    })
    .then(r => r.json())
    .then(data => {
      document.getElementById('pairingQrLoading').style.display = 'none';
      document.getElementById('pairingQrContainer').style.display = 'block';
      document.getElementById('pairingPayload').innerText = data.qr_payload;

      // Load SVG QR
      fetch(data.qr_svg_url)
        .then(res => res.text())
        .then(svg => {
          document.getElementById('qrSvgWrapper').innerHTML = svg;
        });

      let remaining = data.ttl_seconds || 600;
      const countEl = document.getElementById('pairingCountdown');
      countEl.innerText = remaining;
      if (countdownTimer) clearInterval(countdownTimer);
      countdownTimer = setInterval(() => {
        remaining -= 1;
        if (remaining <= 0) {
          clearInterval(countdownTimer);
          countEl.innerText = 'EXPIRED';
        } else {
          countEl.innerText = remaining;
        }
      }, 1000);
    })
    .catch(err => {
      document.getElementById('pairingQrLoading').innerText = 'Failed to generate pairing token: ' + err;
    });
  }

  function closePairingModal() {
    document.getElementById('pairingModal').classList.remove('show');
    if (countdownTimer) clearInterval(countdownTimer);
  }
</script>
{% endblock %}
"""

TEMPLATES = {
    "base.html": BASE_TEMPLATE,
    "login.html": LOGIN_TEMPLATE,
    "dashboard.html": DASHBOARD_TEMPLATE
}

template_env = jinja2.Environment(
    loader=jinja2.DictLoader(TEMPLATES),
    autoescape=jinja2.select_autoescape(["html", "xml"])
)
template_env.globals["url_for"] = url_for
template_env.globals["get_flashed_messages"] = get_flashed_messages

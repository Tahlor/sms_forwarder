"""Authenticated compatibility API used by the Connected Android edition.

All client operations are backed by the canonical /api/v1/device routes and
re-use the same authorization checks; there is no separate security boundary.
"""
from flask import Blueprint, g, jsonify, request

from .auth import require_device
from .api.devices import (
    ingest_events, list_jobs, list_notifications, acknowledge_job,
    acknowledge_notification, device_settings, self_revoke,
)

connected_bp = Blueprint("connected_client", __name__, url_prefix="/api/device")


def _payload(handler, *args):
    result = handler.__wrapped__(*args)
    response = result[0] if isinstance(result, tuple) else result
    return response.get_json()


@connected_bp.route("/events", methods=["POST"])
@require_device
def events():
    return ingest_events.__wrapped__(g.current_device["id"])


@connected_bp.route("/jobs", methods=["GET"])
@require_device
def pending_jobs():
    device_id = g.current_device["id"]
    sms = _payload(list_jobs, device_id).get("jobs", [])
    notifications = _payload(list_notifications, device_id).get("notifications", [])
    # Phone and correspondent identities remain distinct; this is ONLY
    # the authenticated phone's own queue, never jobs belonging to peers.
    out = [{"kind": "sms", **job} for job in sms]
    out.extend({"kind": "notification", **job} for job in notifications)
    return jsonify({"jobs": out}), 200


@connected_bp.route("/ack", methods=["POST"])
@require_device
def acknowledge():
    data = request.get_json(silent=True) or {}
    job_id = str(data.get("job_id", "")).strip()
    if not job_id:
        return jsonify({"error": "Missing job_id"}), 400
    device_id = g.current_device["id"]
    if job_id.startswith("notif_"):
        return acknowledge_notification.__wrapped__(device_id, job_id)
    if job_id.startswith("job_"):
        return acknowledge_job.__wrapped__(device_id, job_id)
    return jsonify({"error": "Invalid job_id"}), 400


@connected_bp.route("/settings", methods=["GET", "POST", "PATCH"])
@require_device
def settings():
    return device_settings.__wrapped__(g.current_device["id"])


@connected_bp.route("/revoke", methods=["POST"])
@require_device
def revoke():
    return self_revoke.__wrapped__(g.current_device["id"])

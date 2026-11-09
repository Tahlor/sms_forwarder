from flask import Flask, jsonify
from .config import config, Config
from .db import init_db
from .api.pairing import pairing_bp
from .api.devices import devices_bp
from .api.conversations import conversations_bp
from .web.admin import admin_bp
from .connected_client import connected_bp

def create_app(custom_config: Config | None = None) -> Flask:
    cfg = custom_config or config
    app = Flask(__name__)
    app.config["SMS_BRIDGE_CONFIG"] = cfg
    app.secret_key = cfg.secret_key

    # Configure session cookies
    app.config["SESSION_COOKIE_HTTPONLY"] = True
    app.config["SESSION_COOKIE_SAMESITE"] = "Lax"
    app.config["SESSION_COOKIE_SECURE"] = cfg.cookie_secure
    app.config["PERMANENT_SESSION_LIFETIME"] = 86400  # 24 hours

    # Initialize SQLite database with WAL and migrations
    init_db(cfg.db_path)

    # Register blueprints
    app.register_blueprint(pairing_bp)
    app.register_blueprint(devices_bp)
    app.register_blueprint(conversations_bp)
    app.register_blueprint(admin_bp)
    app.register_blueprint(connected_bp)
    # The Connected Android pairing path reuses the same one-time claim handler.
    app.add_url_rule('/api/device/claim', 'connected_client_claim',
                     app.view_functions['api_pairing.claim_pairing'], methods=['POST'])

    @app.route("/health", methods=["GET"])
    def health():
        return jsonify({"status": "ok", "service": "sms-bridge"}), 200

    @app.after_request
    def set_security_headers(response):
        response.headers["Content-Security-Policy"] = (
            "default-src 'self'; "
            "script-src 'self' 'unsafe-inline'; "
            "style-src 'self' 'unsafe-inline'; "
            "img-src 'self' data:; "
            "connect-src 'self'; "
            "frame-ancestors 'none';"
        )
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
        response.headers["Permissions-Policy"] = "geolocation=(), microphone=(), camera=()"
        return response

    return app

def create_asgi_app(custom_config: Config | None = None):
    flask_app = create_app(custom_config)
    from asgiref.wsgi import WsgiToAsgi
    return WsgiToAsgi(flask_app)

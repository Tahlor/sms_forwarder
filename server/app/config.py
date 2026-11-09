import os
import secrets
from pathlib import Path

class Config:
    def __init__(self):
        self.host = os.environ.get("SMS_BRIDGE_HOST", "127.0.0.1")
        self.port = int(os.environ.get("SMS_BRIDGE_PORT", "8765"))
        
        default_db_dir = Path(os.environ.get("SMS_BRIDGE_DATA_DIR", "data"))
        self.data_dir = default_db_dir
        self.db_path = Path(os.environ.get("SMS_BRIDGE_DB_PATH", str(default_db_dir / "sms_bridge.db")))
        
        # Secret key for sessions
        secret_key = os.environ.get("SMS_BRIDGE_SECRET_KEY")
        if not secret_key:
            # Check or generate a persistent local session secret file
            secret_file = self.data_dir / ".session_secret"
            if secret_file.exists():
                try:
                    secret_key = secret_file.read_text().strip()
                except Exception:
                    secret_key = secrets.token_hex(32)
            else:
                secret_key = secrets.token_hex(32)
                try:
                    self.data_dir.mkdir(parents=True, exist_ok=True)
                    secret_file.write_text(secret_key)
                    secret_file.chmod(0o600)
                except Exception:
                    pass
        self.secret_key = secret_key
        
        # Admin credentials
        self.admin_user = os.environ.get("SMS_BRIDGE_ADMIN_USER", "admin")
        self.admin_password_hash = os.environ.get("SMS_BRIDGE_ADMIN_PASSWORD_HASH")
        self.admin_password = os.environ.get("SMS_BRIDGE_ADMIN_PASSWORD")
        
        # Pairing settings
        self.public_url = os.environ.get("SMS_BRIDGE_PUBLIC_URL", f"http://{self.host}:{self.port}").rstrip("/")
        self.pairing_ttl_seconds = int(os.environ.get("SMS_BRIDGE_PAIRING_TTL_SECONDS", "600"))
        
        # Cookie security
        secure_cookie_env = os.environ.get("SMS_BRIDGE_SECURE_COOKIE", "").lower()
        self.cookie_secure = secure_cookie_env in ("1", "true", "yes")
        
        # Reverse proxy trust
        self.trusted_proxy = os.environ.get("SMS_BRIDGE_TRUSTED_PROXY", "0").lower() in ("1", "true", "yes")

config = Config()

def get_config() -> Config:
    try:
        from flask import current_app, has_request_context
        if current_app and "SMS_BRIDGE_CONFIG" in current_app.config:
            return current_app.config["SMS_BRIDGE_CONFIG"]
    except Exception:
        pass
    return config

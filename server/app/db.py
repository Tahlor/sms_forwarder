import os
import sqlite3
import threading
from pathlib import Path
from contextlib import contextmanager

_local = threading.local()

def create_connection(db_path: Path | str) -> sqlite3.Connection:
    path = Path(db_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(
        str(path),
        timeout=10.0,
        detect_types=sqlite3.PARSE_DECLTYPES,
        check_same_thread=False
    )
    conn.row_factory = sqlite3.Row
    # Configure SQLite WAL mode and integrity settings
    conn.execute("PRAGMA journal_mode = WAL;")
    conn.execute("PRAGMA synchronous = NORMAL;")
    conn.execute("PRAGMA foreign_keys = ON;")
    conn.execute("PRAGMA busy_timeout = 5000;")
    return conn

class DatabaseManager:
    def __init__(self, db_path: Path | str):
        self.db_path = Path(db_path)
        self._init_lock = threading.Lock()

    def get_connection(self) -> sqlite3.Connection:
        if not hasattr(_local, "connections"):
            _local.connections = {}
        path_str = str(self.db_path)
        if path_str not in _local.connections:
            _local.connections[path_str] = create_connection(self.db_path)
        return _local.connections[path_str]

    @contextmanager
    def transaction(self):
        conn = self.get_connection()
        try:
            yield conn
            conn.commit()
        except Exception:
            conn.rollback()
            raise

    def run_migrations(self, migrations_dir: Path | str = None):
        with self._init_lock:
            conn = self.get_connection()
            if migrations_dir is None:
                migrations_dir = Path(__file__).parent / "migrations"
            else:
                migrations_dir = Path(migrations_dir)

            conn.execute("""
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version INTEGER PRIMARY KEY,
                    name TEXT NOT NULL,
                    applied_at TEXT NOT NULL DEFAULT (datetime('now'))
                );
            """)
            conn.commit()

            cursor = conn.cursor()
            cursor.execute("SELECT version FROM schema_migrations;")
            applied_versions = {row[0] for row in cursor.fetchall()}

            sql_files = sorted(migrations_dir.glob("*.sql"))
            for sql_file in sql_files:
                # Expect filenames like 001_initial_schema.sql
                parts = sql_file.name.split("_", 1)
                if not parts[0].isdigit():
                    continue
                version = int(parts[0])
                if version not in applied_versions:
                    script = sql_file.read_text(encoding="utf-8")
                    with self.transaction() as tx:
                        tx.executescript(script)
                        tx.execute(
                            "INSERT INTO schema_migrations (version, name) VALUES (?, ?);",
                            (version, sql_file.name)
                        )

db_manager: DatabaseManager | None = None

def init_db(db_path: Path | str) -> DatabaseManager:
    global db_manager
    db_manager = DatabaseManager(db_path)
    db_manager.run_migrations()
    return db_manager

def get_db() -> sqlite3.Connection:
    if db_manager is None:
        raise RuntimeError("DatabaseManager has not been initialized. Call init_db() first.")
    return db_manager.get_connection()

@contextmanager
def db_transaction():
    if db_manager is None:
        raise RuntimeError("DatabaseManager has not been initialized. Call init_db() first.")
    with db_manager.transaction() as conn:
        yield conn

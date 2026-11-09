FROM python:3.12-slim

# Prevent python from writing pyc files and buffering stdout/stderr
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    SMS_BRIDGE_HOST=0.0.0.0 \
    SMS_BRIDGE_PORT=8765 \
    SMS_BRIDGE_DATA_DIR=/data \
    SMS_BRIDGE_DB_PATH=/data/sms_bridge.db

WORKDIR /app

# Install system dependencies (gosu for safe privilege dropping)
RUN apt-get update && \
    apt-get install -y --no-install-recommends gosu && \
    rm -rf /var/lib/apt/lists/*

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application source & entrypoint
COPY entrypoint.sh .
COPY run.py .
COPY app/ ./app/

# Create unprivileged runtime user and data directory
RUN useradd -u 1000 -m -s /bin/bash smsbridge && \
    mkdir -p /data && \
    chmod +x /app/entrypoint.sh && \
    chown -R smsbridge:smsbridge /app /data

VOLUME ["/data"]

EXPOSE 8765

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD python3 -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8765/health', timeout=3)" || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["python3", "run.py"]

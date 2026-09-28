FROM python:3.12-slim
COPY requirements.txt /tmp/avora-requirements.txt
RUN pip install --no-cache-dir -r /tmp/avora-requirements.txt
WORKDIR /app
CMD ["python", "-m", "avora_nas.server"]

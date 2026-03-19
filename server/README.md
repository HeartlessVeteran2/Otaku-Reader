# Otaku Reader Sync Server

Lightweight self-hosted sync server for Otaku Reader. Simple HTTP API for backing up and restoring your manga library data.

## Quick Start

### Using Docker Compose (Recommended)

```bash
cd server

# Create a .env file for your auth token
echo "AUTH_TOKEN=your-secure-random-token" > .env

# Start the server
docker-compose up -d

# Check status
docker-compose ps
```

### Using Docker Run

```bash
docker build -t otaku-reader-sync .

docker run -d \
  --name otaku-reader-sync \
  -p 8080:8080 \
  -e AUTH_TOKEN=your-secure-random-token \
  -v $(pwd)/data:/app/data \
  --restart unless-stopped \
  otaku-reader-sync
```

### Building from Source

```bash
# From project root
./gradlew :server:shadowJar

# Run the JAR
java -jar server/build/libs/server.jar
```

## Configuration

Environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `AUTH_TOKEN` | `otaku-reader-default-token-change-me` | **Required** Authentication token |
| `HOST` | `0.0.0.0` | Server bind address |
| `PORT` | `8080` | Server port |
| `STORAGE_PATH` | `/app/data` | Path for storing sync data |

## API Endpoints

All sync endpoints require Bearer token authentication.

### Health Check (No auth)
```
GET /health
```
Returns: `OK`

### Upload Snapshot
```
POST /sync/upload
Authorization: Bearer <token>
Content-Type: application/json

{
  "data": "<base64-encoded-json-snapshot>",
  "timestamp": 1712345678901
}
```

### Download Snapshot
```
GET /sync/download
Authorization: Bearer <token>
```
Returns:
```json
{
  "data": "<base64-encoded-json-snapshot>",
  "timestamp": 1712345678901,
  "exists": true
}
```

### Get Timestamp
```
GET /sync/timestamp
Authorization: Bearer <token>
```
Returns:
```json
{
  "timestamp": 1712345678901,
  "exists": true
}
```

### Delete Snapshot
```
DELETE /sync
Authorization: Bearer <token>
```

## Security

- Always change the default `AUTH_TOKEN`
- Use HTTPS when exposing to the internet (via reverse proxy)
- Store the auth token securely in the app
- The server is designed for single-user personal use

## Reverse Proxy Example (Nginx)

```nginx
server {
    listen 443 ssl;
    server_name sync.yourdomain.com;

    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

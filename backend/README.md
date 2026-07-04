# MyRealTV VPS Sync Server & Database Setup

This is the backend server that coordinates database synchronization for **MyRealTV** clients. It creates an SQLite database and exposes sync endpoints. It also serves the server-driven Home Screen UI config (`config.json`).

## Setup Steps

### 1. Install Node.js
If Node.js is not yet installed on your VPS, install it. For example, on Ubuntu/Debian:
```bash
sudo apt update
sudo apt install -y nodejs npm
```

### 2. Copy Files to VPS
Create a folder `/opt/myrealtv-backend` on your VPS and copy the following files from this directory to that folder:
- `server.js`
- `config.json`

### 3. Initialize Project & Install Dependencies
Navigate to the directory on your VPS and run:
```bash
cd /opt/myrealtv-backend
npm init -y
npm install express sqlite3
```

### 4. Run the Server
You can run the server directly:
```bash
node server.js
```
The server will start listening on port `3000` on all network interfaces (including your Tailscale interface).

### 5. (Recommended) Run in Background using PM2
To keep the server running in the background and survive restarts, use `pm2`:
```bash
sudo npm install -y -g pm2
pm2 start server.js --name "myrealtv-sync"
pm2 save
pm2 startup
```

## How Sync Works in the App
The Android TV app is pre-configured to point to:
- Sync Base URL: `https://sync.myreal.cc/`
- Config JSON: `https://sync.myreal.cc/config.json`

The app uses Room SQLite locally and pushes/pulls changes from the endpoints above.

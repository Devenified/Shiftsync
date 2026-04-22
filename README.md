# ShiftSync

ShiftSync is an Android + Node.js shift management app with separate worker and employer flows.

## Stack

- Android app: Java, Material Components, Gradle
- Backend: Node.js, Express, MongoDB, JWT auth

## Backend Setup

1. Copy `backend/.env.example` to `backend/.env`.
2. Set real values for:
   - `MONGO_URI`
   - `JWT_SECRET`
   - `GEMINI_API_KEY` if you want AI features enabled
3. Install dependencies:

```powershell
cd backend
npm install
```

4. Start the server:

```powershell
npm start
```

The backend listens on `PORT` from `.env`, default `3000`.

Health check:

```text
GET /health
```

## Android Setup

The Android app now supports a configurable backend base URL through a Gradle property or environment variable:

- Gradle property: `SHIFTSYNC_BASE_URL`
- Environment variable: `SHIFTSYNC_BASE_URL`

If neither is set, it defaults to:

```text
http://10.0.2.2:3000
```

Examples:

```powershell
$env:SHIFTSYNC_BASE_URL='http://10.0.2.2:3000'
cd android-app
./gradlew.bat assembleDebug
```

For a physical device, set `SHIFTSYNC_BASE_URL` to your backend’s LAN or public URL before building.

## Verified

- Backend changed routes/controllers pass `node --check`
- Android app builds successfully with:

```powershell
cd android-app
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
./gradlew.bat assembleDebug
```

## Current Functional Coverage

- Worker login, dashboard, open shifts, my shifts, notifications
- Employer login, dashboard, post/manage shifts, worker search, notifications
- Leave requests now use real backend endpoints
- Shift swap requests now use real backend endpoints
- Notifications and activity feed use live backend data instead of mock-only flows

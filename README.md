# Chaty — Real-Time Messenger for Android

Chaty is an original, lightweight, real-time messaging application for Android engineered entirely on Google Firebase's free Spark plan. It offers instant message synchronization, live user presence, message editing, soft deletion, and unread message tracking without requiring any custom servers or paid cloud subscriptions.

---

## Key Features

- **Instant Real-Time Messaging**: Built on Firebase Realtime Database with sub-second message delivery.
- **Smart Quoted Replies**: Reply directly to any incoming or outgoing message with compact embedded quote previews.
- **Message Editing & Deletion**: Edit typos or delete sent messages with server-side authorization enforcement.
- **Live User Presence**: Real-time online status and "last seen" tracking via Firebase `.info/connected` and `onDisconnect()` handlers.
- **Live Typing Indicator**: Microinteraction showing when your contact is composing a message in real time.
- **Atomic Username Reservation**: Enforce unique, safe alphanumeric `@username` handles using database transactions and security rules.
- **Responsive & Adaptive UI**: Mobile-first single-pane layout on phones; automatically transforms into a dual-pane sidebar layout on foldables, tablets, and desktop displays.
- **Original Visual Identity**: Obsidian slate and electric cyan aesthetic with Material 3 theming, dark/light mode toggles, and customized vector avatars.
- **Spark Plan Friendly**: Designed to operate within Firebase's 100 simultaneous connections and 1 GB free bandwidth limits without paid infrastructure.

---

## Architecture & Database Design

Chaty strictly utilizes Firebase as its only backend. Data is partitioned into normalized nodes to minimize bandwidth and eliminate redundant listeners:

```
/
├── usernames/
│   └── {usernameLower}               # Atomic reservation: { uid, createdAt }
│
├── users/
│   └── {uid}                         # Discoverable profile: { uid, username, displayName, statusMessage, avatarColorIndex, createdAt, lastSeen, isOnline }
│
├── presence/
│   └── {uid}                         # Disconnect-handled status: { isOnline, lastSeen }
│
├── user_conversations/
│   └── {uid}/
│       └── {conversationId}          # Flat list metadata: { id, otherUid, otherUsername, otherDisplayName, otherAvatarIndex, lastMessage, lastMessageTimestamp, unreadCount }
│
├── conversations/
│   └── {conversationId}              # Membership verification: { id, participants: { uid1: true, uid2: true }, createdAt, lastActivity }
│
├── messages/
│   └── {conversationId}/
│       └── {messageId}               # Message payload: { id, conversationId, senderId, senderUsername, senderDisplayName, text, timestamp, status, replyToId, replyToText, replyToSender, isEdited, editedAt, isDeleted }
│
└── typing/
    └── {conversationId}/
        └── {uid}                     # Ephemeral typing state: { isTyping, timestamp }
```

### Deterministic Conversation IDs
To prevent duplicate conversation threads when two users message each other, 1-on-1 conversations use deterministic IDs:
```kotlin
val conversationId = if (uidA < uidB) "chat_${uidA}_${uidB}" else "chat_${uidB}_${uidA}"
```

---

## Firebase Setup Instructions

Follow these steps to configure your Firebase project:

### 1. Create Firebase Project
1. Go to the [Firebase Console](https://console.firebase.google.com/).
2. Create a new project (e.g., `chaty-messenger`).
3. Select the free **Spark Plan** (no billing required).

### 2. Enable Firebase Authentication
1. Navigate to **Build > Authentication > Sign-in method**.
2. Enable **Email/Password** provider (Email link is optional).
3. Save changes.

### 3. Create Realtime Database
1. Navigate to **Build > Realtime Database**.
2. Select your cloud region (e.g. `asia-southeast1` or `us-central1`).
   - For regional instances, the database URL follows: `https://<PROJECT-ID>-default-rtdb.<REGION>.firebasedatabase.app`
3. Start in **Locked Mode**.

### 4. Deploy Security Rules
In the Firebase Console, go to **Realtime Database > Rules**, paste the contents of `database.rules.json` from the root of this repository, and click **Publish**.

Alternatively, deploy using the Firebase CLI:
```bash
firebase deploy --only database
```

### 5. Download Configuration File
1. In Project Settings, add an Android app with package name: `com.chaty`.
2. Download `google-services.json` and place it in the `/app` directory:
   ```
   /app/google-services.json
   ```

---

## Security Model

Security is enforced at the server level via `database.rules.json`:
- **Strict Authentication**: Unauthenticated reads and writes are rejected globally.
- **Self-Only Profile Writes**: Users can only modify their own `/users/{uid}` and `/presence/{uid}` records.
- **Reservation Lock**: `/usernames/{username}` can only be claimed if it doesn't already exist.
- **Conversation Confidentiality**: Only registered participants in `/conversations/{conversationId}/participants` can read and write to `/messages/{conversationId}`.
- **Sender Integrity**: Users can only author messages where `senderId == auth.uid`, and only original senders can update or delete their messages.
- **Data Validation**: Enforces string length restrictions on messages, usernames, and profiles.

---

## Exporting & Maintaining Source Code

### Download Source as ZIP
1. In the AI Studio editor header, click the settings or export menu.
2. Select **Export as ZIP** to download the complete codebase.
3. If using GitHub, navigate to your repository and click **Code > Download ZIP**.

### Building Locally with Gradle
To assemble the debug or release APK locally:
```bash
# Run unit tests
gradle :app:testDebugUnitTest

# Assemble Release APK
gradle :app:assembleRelease
```
The output APK is generated at `app/build/outputs/apk/release/app-release.apk`.

---

## Known Limitations & Future Roadmap

To preserve the zero-cost Spark plan requirements, the following features are intentionally deferred:

1. **Media Attachments (Images, Video, Voice)**: Firebase Storage requires careful quota management; file attachments can be added in a future release using Cloud Storage for Firebase.
2. **Push Notifications (FCM)**: Push notifications when the app is completely terminated typically require a server-side Cloud Function to trigger FCM; can be added via Cloud Functions on the Blaze plan or a free notification microservice.
3. **Group Conversations**: The current MVP focuses on high-speed direct 1-on-1 messaging; group channels can easily reuse the conversation model by expanding `participants`.

## Week 3 Task - Message Attachments Implementation

### Problem Statement
The chat application lacked support for sending file attachments (PDFs, audio files) which limited communication to only text and images. Users needed a way to share documents and voice messages while maintaining a consistent UI experience.

### Solution Overview
Implemented a file attachment system that:
1. Uses `ActivityResultLauncher` with MIME type filtering
2. Uploads files to Cloudinary  Storage with organized folder structure
3. Stores message metadata in Firestore
4. Displays files in message bubbles with type-specific icons
5. Shows upload progress with black-themed UI


## Key Technical Decisions

### 1. Cloudinary vs Firebase Storage
- **Chose Cloudinary because**:
  - Free tier offers
  - Built-in transformations (PDF thumbnails, audio waveform generation)
  - Automatic format optimization
  - Better CDN performance globally
  - **Firebase Blaze Plan was avoided** to prevent unexpected costs from storage overages
    
 ### 1. Dynamically Ui With animation
- **Typing Input Field**:
  - User type anything then hide the imagePicker icon Same as whatsapp
  - Dynamically audio or send button

 ## How to run/test
1. Launch app and In ui 2 loginButton you can select any for testing you can select the open chat room (Static chatrrom for testing).
2. Text Message: With 2 person user 1 or user 2
3. File Attachment: select image or select pdf/ audio send
    
### Demo
(https://github.com/user-attachments/assets/8e41ff10-e57f-448f-a2a8-63267e2a9498)  


- Files updated:
  - `ChatScreen.kt`
  - `MessageBubble.kt`
  - `ChatViewModel.kt`
### Stretch Task
1. Implemented Support image preview,
2. Added PDF icon / and audio icon
3. Audio Feature not implemented only done Ui not implemented backend

### EOD 11-8-25 ###

-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
Week 3 Task 
 Resend Failed Messages & Delivery Receipts &Chat Export 
Implemented Functionality
1. Chat Export System

Components: ChatExporter.kt — Handles export logic and ExportDialog.kt — User interface for selecting export formats.

Supported Formats:
TXT Export: Generates messages.txt containing a readable conversation history.
JSON Export: Creates messages.json with complete structured message data.
ZIP Export: Currently in progress, some issues remain.

Implementation Details: Uses Android's FileProvider for secure file access and sharing. Implements Android share Intents to forward exported files.
Uses Firestore batch operations for efficient bulk deletion of chat data. Performs export and deletion operations using coroutines to run processes in the background.

2. Automatic Retry for Failed Messages

What it does: Monitors messages marked as FAILED status. Automatically retries sending these messages when the device reconnects to the internet.
Updates message statuses in real-time, showing transitions like Sending → Sent → Delivered → Seen. Allows users to manually retry sending if automatic attempts fail.

Main Components: Network Watcher: Detects when internet connectivity is restored and triggers the retry process. 
Retry Handler: Attempts to resend failed messages with exponential backoff intervals (1 second, then 2 seconds, then 4 seconds). Stops after 3 failed attempts.
Status Tracker: Keeps message status updated locally and syncs with Firestore, reflecting message states such as sending, sent, delivered, and seen.

Technical Details: Uses WorkManager to manage retry tasks reliably, even if the app is closed. Stores message states in a local database(ROOM) for offline access.
Synchronizes message status updates with Firestore backend.

Why it matters: Prevents loss of messages when network connection is unstable. Keeps users informed about the delivery status of their messages.

EOD(8-8-25)

-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------



# QuickChat - Day 1: Chat UI Layout

## 📱 Project Overview (Day 1)
Implemented a chat screen UI with dummy data featuring:
- Message list with sender-specific bubbles
- Input field with send button
- Timestamp display
- System messages and date separators (stretch goals)
- Minimum SDK: 24 (Android 7.0)

## 🏗️ MVVM Architecture

<pre>
┌────────────────────┐     ┌────────────────────────┐     ┌────────────────────┐
│    Composables     │ <-- │      ViewModel         │ <-- │   Dummy Repository  │
│  (ChatScreen.kt)   │     │  (ChatViewModel.kt)    │     │  (Fake Messages)    │
└────────────────────┘     └────────────────────────┘     └────────────────────┘
</pre>



### Key Components:
1.View Layer (ChatScreen.kt):

Manages all UI rendering

Handles user interactions

Observes ViewModel state
2.ViewModel Layer (ChatViewModel.kt):
  Maintains StateFlow<ChatUiState>

Processes business logic

Formats and structures message data

Handles date separator insertion
3. Model Layer:
ChatMessage.kt: Data class structure

ChatUiState.kt: Sealed class for UI states

FakeChatRepository: Dummy data source

## 🎨 UI Logic & Features
### Implemented:
- **Message List**: `LazyColumn` with reverse layout
- **Message Bubbles**: 
  - Right-aligned (black) for current user
  - Left-aligned (gray) for others
  - Centered (neutral) for system messages
- **Auto-scroll**: New messages automatically appear
- **Input Field**: With send button (disabled when empty)
- Automatic message list updating
  Stretch Features:
  System Messages:

Distinct visual styling

Automatic generation for join/leave events

Special formatting in message list

Date Separators:

Automatic insertion between message groups

Relative date formatting ("Today", "Yesterday")

Full date formatting for older messages

Timezone-aware display

## ✅ Day 1 Deliverables
Complete chat screen implementation

Fully functional message input system

Proper sender-based message styling

Accurate timestamp display

Correct data model implementation

Proper ViewModel state management

Comprehensive UI state handling

## 🛠️ Setup & Test
1. Clone repository
2. Open in Android Studio
3. Build and run on:
   - Emulator (API 24+)
   - Physical device (Android 7.0+)

No Firebase setup needed (using dummy data)

## 📽️ Demo Video
[https://github.com/user-attachments/assets/c783e053-d161-4180-b1ee-51777f36fc31]

1. Loading state
2. Message display with bubbles
3. Sending new message
4. Auto-scroll behavior
5. System messages

[End of Day 1]

# QuickChat - Day 2: Firebase Integration
## 🔥 Firebase Implementation


### ✅ What was done
- Integrated **Firebase Firestore** for real-time chat syncing
- Wrapped Firebase calls using a **sealed `Result<T>` class** for clean error handling
- Added **input validation** to limit message input to **300 characters**
- Implemented **message delivery status** using a `MessageStatus` sealed class and dynamic icons:
  - 🕐 `SENDING`
  - ✅ `SENT`
  - ❌ `FAILED`
- Updated ViewModel and Repository to use state management with sealed classes
- Fixed UI issues related to `TopAppBar` and message rendering

---

### 💡 Design Decisions
- ✅ Chose **Firestore** over Realtime DB:
  - Built-in offline support
  - Better scalability and query support
  - Real-time listeners simplify UI state sync
- ✅ Used `LaunchedEffect` and `snapshotFlow` for scroll position behavior
- ✅ Used manual check for character limit (`message.length <= 300`)
- ✅ Added online/offline **status icon** for better UX

---

### 📂 Firebase Firestore Structure

Collections:
└── chats  
    └── {chatId} (document)  
        └── messages (subcollection)  
            └── {messageId} (document)  
                ├── id: String  
                ├── senderId: String  
                ├── text: String  
                ├── timestamp: Long  
                ├── senderName: String  
                └── isOnline: Boolean

---

## 📸 Firebase Console Screenshot (Firestore Structure)

![Firestore Structure](https://github.com/user-attachments/assets/94dedd93-33b1-40a9-adbe-7331b249a94e)

---

## 🎥 Demo Video

[![Day 2 Demo Video](https://github.com/user-attachments/assets/15ffe797-d538-4eb7-bb94-057282528923)](https://github.com/user-attachments/assets/15ffe797-d538-4eb7-bb94-057282528923)
proper headers, emoji, table, code formatting, and bullet styles):


###                                                ###
## 🔄 QuickChat – Day 3: Real-Time Message Listener

### ✅ What was done

Today we implemented **real-time chat updates** using Firebase Firestore’s `addSnapshotListener` and enhanced message handling through Room for **offline caching**.

---

### 📋 Feature Summary

| Feature                        | Status  |
|-------------------------------|---------|
| Firestore real-time listener  | ✅ Done |
| UI auto-update on new message | ✅ Done |
| Timestamp-based sorting       | ✅ Done |
| Auto-scroll for own messages  | ✅ Done |
| Listener deduplication        | ✅ Done |
| Memory leak prevention        | ✅ Done |
| Room offline caching          | ✅ Done |
| Sync gaps on reconnect        | ✅ Done |

---

### 📡 Listener Lifecycle Management

- Attached once per chat room using `addSnapshotListener`.
- Listener is **removed on dispose** to prevent memory leaks.
- Ensured only **one active listener** per room.
- Used `distinctUntilChanged` and message IDs to **avoid duplicates**.

---

### 🧠 Message Deduplication

- Each message has a **unique ID** (`UUID`).
- ViewModel keeps a `Set<String>` of received IDs.
- New messages are checked against cache to **avoid double insertion**.

---

### 🧭 Auto-Scroll Behavior

- Auto-scroll is triggered **only for messages sent by self**.
- Incoming messages from others **do not auto-scroll**, preserving the scroll position.
- Implemented using `LazyListState.isScrolledToBottom` and `snapshotFlow`.

---

### 🗂️ Offline Caching (Room)

- Messages are saved in **Room** for offline access.
- On reconnect:
  - Loads cached messages via `loadCachedMessages()`.
  - Syncs missing messages from Firebase using **timestamp comparison**.

---

### 🧪 Testing & Observations

- ✅ Cached messages load instantly after restart.
- ✅ Works in offline mode (read-only).
- ✅ On reconnect, missing messages are synced.
- ✅ Confirmed real-time sync with **two-device testing**.

---

### 📽️ Demo Video 
###  Check your email
**▶️ Day 3 – Two Device Real-Time Chat Demo**

Includes:
- Sending/receiving messages across devices
- Live UI updates
- Offline fallback
- Reconnect + sync logic

---

### ✅ Day 3 Deliverables

- ✅ Firebase Firestore real-time listener
- ✅ UI state updates with ViewModel
- ✅ Offline caching using Room
- ✅ Reconnect sync logic
- ✅ Message deduplication and scroll logic

### Room Test in Logcat ![WhatsApp Image 2025-07-31 at 16 47 28_e7eee87a](https://github.com/user-attachments/assets/421431c8-f9a9-4316-a544-0506eda2ac9f)



## 💬 QuickChat – Day 4: Chatroom List + Unread Count (Updated)

---

### ✅ What was done

Today we implemented the **Chatroom List UI** showing:
 
This enhances user experience by surfacing conversation context without opening each room.

---

### 📋 Feature Summary

| Feature                                | Status  |
|---------------------------------------|---------|
| `ChatRoomListScreen` UI               | ✅ Done |
| `ChatRoom` data model                 | ✅ Done |
| Firebase Firestore room fetching      | ✅ Done |
| Display avatar, name, last message    | ✅ Done |
| Swipe to delete/archive               | ✅ Done |
| Timestamp                             | ✅ Done |
| Unread count calculation              | ✅ Done |
| Real-time unread count updates        | ✅ Done |
| Mute/unmute per room UI States Problem| ✅ Done |

---

### 🧱 ChatRoom Data Model

```kotlin
data class ChatRoom(
    val roomId: String,
    val lastMessage: String?,
    val lastTimestamp: Long,
    val unreadCount: Int
)
```

---

### 🔄 Unread Count Logic

- Each user has their own `lastRead_<userId>` timestamp
- Unread count calculated by counting messages after this timestamp
- Badges update in real-time

#### 🧩 Unread Count Calculation

```kotlin
fun calculateUnreadCount(
    messages: List<Message>,
    lastReadTimestamp: Long
): Int {
    return messages.count { it.timestamp > lastReadTimestamp }
}
```

#### ✅ Marking Messages as Read

```kotlin
fun markMessagesAsRead(roomId: String, userId: String) {
    val currentTimestamp = System.currentTimeMillis()
    val firestore = FirebaseFirestore.getInstance()
    firestore.collection("chatrooms")
        .document(roomId)
        .update("lastRead_$userId", currentTimestamp)
}
```

> Call `markMessagesAsRead()` when the user opens the chat screen to reset unread count.

#### 🧮 Unread Count Flow Diagram

![Unread Count Diagram] (<img width="2790" height="1449" alt="deepseek_mermaid_20250802_7d28b7" src="https://github.com/user-attachments/assets/010e5f7d-f624-4b09-bc35-d597100a645d" />)

---

### 🧪 Real-Time Unread Tracking

- Used Firestore snapshot listener on `messages` subcollection.
- Listener checks timestamp against user’s `lastReadTimestamp`.
- `ChatRoomListScreen` reflects changes instantly using `StateFlow`.

---

### 🧭 UI Elements

- **Avatar**: loaded from user profile.
- **Name**: derived from chat participant ID.
- **Last message**: shows text preview or media tag.
- **Time**: human-readable (e.g., "5 mins ago")
- **Unread count**: visible as a badge (hidden if zero).


- Tap on any chatroom to navigate to the full chat screen.

---

### ✅ Day 4-5 Deliverables

- ✅ ChatRoom model created
- ✅ Firebase fetch for current user's chatrooms
- ✅ Last message + time displayed
- ✅ Room navigation works with live data
- ✅Calculated unread Count messages
- ✅Swipe to delete/archive chatroom
- ✅Mute/Unmute UI


[End of Day 4 & 5]

# 💬 QuickChat – Day 6: Image Messaging Feature 

## 🎯 Goal
Send image messages and handle upload progress in a chat application.

## ✅ Required Tasks
- [x] Use `ActivityResult` to select image from device.
- [x] Upload to Cloudinary: `/chatrooms/{roomId}/images/{messageId}.jpg`
- [x] Display upload progress indicator while uploading.
- [x] Send image message with `imageUrl` once upload completes.
- [x] Display image messages inside chat UI.

## 🚀 Stretch Tasks (Optional)
- [x] Image compression before upload.
- [x] Thumbnail support (for preview and faster loading).
- [x] Enforce file size limit (e.g., 5MB max).

## 🧩 Data Model

```
data class ChatMessage(
    val id: String,
    val text: String? = null,
    val imageUrl: String? = null,
    val senderId: String,
    val timestamp: Long,
    val messageType: MessageType
)

enum class MessageType {
    TEXT, IMAGE
}
```

## ☁️ Upload Strategy
- Upload selected image to **Cloudinary** using a preset and API key.
- Once uploaded, get the `secure_url` of the image.
- Send a message with `messageType = IMAGE` and `imageUrl = secure_url`.

  ## 🧩 Image Message Schema (Data Structure
```
{
  "id": "msg_123",
  "senderId": "user_456",
  "roomId": "room_789",
  "type": "image",
  "imageUrl": "https://res.cloudinary.com/your-cloud/image/upload/v1690000000/chat_images/image123.jpg",
  "timestamp": 1690000000000,
  "uploadProgress": 100
}
```


## ✅ Day 6 Deliverables
- ✅ Image selection from device
- ✅ Upload progress
- ✅ mage displayed in chat UI
- ✅ Image compression
- ✅ Thumbnail support
- ✅ Validarion only 5mb files send




---

✅ [End of Day 6] 




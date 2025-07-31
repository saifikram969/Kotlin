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








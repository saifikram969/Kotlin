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






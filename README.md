# QuickChat - Day 1: Chat UI Layout

## 📱 Project Overview (Day 1)
Implemented a chat screen UI with dummy data featuring:
- Message list with sender-specific bubbles
- Input field with send button
- Timestamp display
- System messages and date separators (stretch goals)
- Minimum SDK: 24 (Android 7.0)

## 🏗️ MVVM Architecture
┌────────────────────┐     ┌────────────────────────┐     ┌────────────────────┐
│    Composables     │ <-- │      ViewModel         │ <-- │   Dummy Repository  │
│   (ChatScreen.kt)  │     │   (ChatViewModel.kt)   │     │  (Fake Messages)    │
└────────────────────┘     └────────────────────────┘     └────────────────────┘


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

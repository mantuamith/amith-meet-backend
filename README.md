# Chat App Microservices Starter Project

Generated scaffold for Spring Boot-based microservices chat application.


Core Functionality Implemented
Feature	Description
Message Persistence	All messages (group or direct) are saved to MongoDB via messageRepository.save(message).
Sender Tracking	Sender is auto-injected using Principal.getName() from the WebSocket session.
Direct Messaging	Messages sent to specific users are routed using /queue/messages.
Group Messaging	Uses GroupClient.getGroupById(...) to retrieve members and deliver messages to each (excluding sender).
Chat History Retrieval	REST endpoint /messages/history?user1=&user2= fetches last 100 messages in either direction.

⚠️ Known Gaps / Not Yet Implemented
Gap / Enhancement Area	Notes
Message Read/Delivery Status	No flags for READ, DELIVERED, etc. Add optional field status in Message schema (e.g., PENDING, SENT, DELIVERED, READ).
File/Media Attachments	No support for images, docs, etc. Requires storing metadata + file storage (e.g., S3, local disk, or DB).
Delivery Acknowledgment	No WebSocket acknowledgment tracking. Could use a messageId + /ack route later.
Cleaner Layered Design	No dedicated ChatServiceImpl. Logic is directly in controller. Could be moved to a ChatService for cleaner separation.
Message Typing Indicator	Not implemented but can be added for UX.
Group Join/Leave Notifications	Not yet in place. Might be useful for real-time updates.

🛠️ Next Steps (Optional Enhancements)
Add a status field to Message:

java
Copy
Edit
public enum MessageStatus { SENT, DELIVERED, READ }
private MessageStatus status;
Create a ChatServiceImpl to extract logic from the controller.

Add REST/WebSocket endpoints for:

Marking message as read

Uploading/receiving attachments

Real-time status/typing indicators

API Summary
Endpoint / Mapping	Type	Description
/chat	WebSocket	Handles real-time messaging (group/direct)
/messages/history	GET	Fetches recent messages between two users

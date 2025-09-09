 Add compound index on (senderKey, receiverKey, timestamp, _id) and migrate queries to use keys.

 Start setting receiverKey via server lookup (or FE sends it) and backfill old docs.

 Add optional key fields to ResetUnreadRequest, ReadReceipt, DeliveryReceipt, etc., and accept either username or key.

 (Optional) Switch STOMP routing to keys:

change StompUserPrincipal#getName() to return userKey

route via convertAndSendToUser(<receiverKey>, "/queue/...")

keep username fallback for a grace period

 (Optional) Add deletedForUserKeys set and move “delete for me” to UUIDs.
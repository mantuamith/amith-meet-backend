import type { SenderKeyRecord } from "./SenderKeyRecord";
import type { SignalProtocolAddress } from "../protocol/SignalProtocolAddress";
import type { UUID } from "./UUID";

/**
 * TypeScript equivalent of Signal's SenderKeyStore interface.
 *
 * Stores SenderKeyRecords indexed by: (distributionId + senderName + deviceId)
 */
export interface SenderKeyStore {

  /**
   * Store the SenderKeyRecord for a given sender + distribution ID.
   */
  storeSenderKey(
    sender: SignalProtocolAddress,
    distributionId: UUID,
    record: SenderKeyRecord
  ): void;

  /**
   * Load a copy of the SenderKeyRecord for the given sender + distribution ID.
   *
   * Returns:
   *   - a copy of the stored SenderKeyRecord
   *   - or null if none exists
   */
  loadSenderKey(
    sender: SignalProtocolAddress,
    distributionId: UUID
  ): SenderKeyRecord | null;
}
import type { SignalProtocolAddress } from "../../SignalProtocolAddress";
import type { SenderKeyRecord } from "./SenderKeyRecord";

/**
 * Equivalent of:
 * org.signal.libsignal.protocol.groups.state.SenderKeyStore
 *
 * Implementations MUST:
 * - Persist SenderKeyRecord durably
 * - Return a COPY from loadSenderKey()
 * - Treat (distributionId + sender + deviceId) as the unique key
 */
export interface SenderKeyStore {
  /**
   * Commit to storage the SenderKeyRecord for a given
   * (distributionId + senderName + deviceId) tuple.
   *
   * @param sender The address of the current client.
   * @param distributionId An opaque identifier that uniquely identifies the group
   *                       (not the group ID itself).
   * @param record The SenderKeyRecord to store.
   */
  storeSenderKey(
    sender: SignalProtocolAddress,
    distributionId: string, // UUID as string
    record: SenderKeyRecord
  ): Promise<void> | void;

  /**
   * Load a COPY of the SenderKeyRecord for the given
   * (distributionId + senderName + deviceId) tuple.
   *
   * IMPORTANT:
   * - Must return a copy, not the internal reference.
   * - Mutating the returned record must NOT affect storage
   *   unless storeSenderKey() is called.
   *
   * @param sender The address of the current client.
   * @param distributionId An opaque group identifier (UUID).
   * @returns A copy of SenderKeyRecord, or null if not found.
   */
  loadSenderKey(
    sender: SignalProtocolAddress,
    distributionId: string
  ): Promise<SenderKeyRecord | null> | SenderKeyRecord | null;

  getStoreHandle(): number;
}

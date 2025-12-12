import { SenderKeyRecord } from "../SenderKeyRecord";
import type { SenderKeyStore } from "../SenderKeyStore";
import type { SignalProtocolAddress } from "../../protocol/SignalProtocolAddress";
import type { UUID } from "../UUID";

/**
 * Erasable-safe replacement for Kotlin Pair<SignalProtocolAddress, UUID>.
 */
type StoreKey = readonly [SignalProtocolAddress, UUID];

function makeKey(sender: SignalProtocolAddress, distributionId: UUID): StoreKey {
  return [sender, distributionId] as const;
}

/**
 * TypeScript equivalent of Signal's InMemorySenderKeyStore.
 */
export class InMemorySenderKeyStore implements SenderKeyStore {

  private store = new Map<StoreKey, SenderKeyRecord>();

  constructor() {}

  // ---------------------------------------------------------------------------
  // storeSenderKey()
  // ---------------------------------------------------------------------------

  storeSenderKey(
    sender: SignalProtocolAddress,
    distributionId: UUID,
    record: SenderKeyRecord
  ): void {
    const key = makeKey(sender, distributionId);
    this.store.set(key, record);
  }

  // ---------------------------------------------------------------------------
  // loadSenderKey()
  // ---------------------------------------------------------------------------

  loadSenderKey(
    sender: SignalProtocolAddress,
    distributionId: UUID
  ): SenderKeyRecord | null {
    const key = makeKey(sender, distributionId);
    const record = this.store.get(key);

    if (!record) {
      return null; // matches Java behavior
    }

    try {
      // Return a *copy* so caller cannot mutate stored state
      return new SenderKeyRecord(record.serialize());
    } catch (e) {
      throw new Error(`AssertionError: ${(e as Error).message}`);
    }
  }
}
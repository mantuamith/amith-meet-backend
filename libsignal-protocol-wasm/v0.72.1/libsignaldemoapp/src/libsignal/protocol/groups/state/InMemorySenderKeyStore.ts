
import { senderKeyStore as  senderKeyStoreWasm } from "libsignal_wasm_pqxdh";
import type { SignalProtocolAddress } from "../../SignalProtocolAddress";
import { SenderKeyRecord } from "./SenderKeyRecord";
import type { SenderKeyStore } from "./SenderKeyStore";

/**
 * Equivalent of:
 * org.signal.libsignal.protocol.groups.state.InMemorySenderKeyStore
 *
 * In-memory ONLY.
 * Not persistent.
 */
export class InMemorySenderKeyStore implements SenderKeyStore {
  public readonly storeHandle!: number;
  
  constructor() {
    this.storeHandle = senderKeyStoreWasm.senderkeystore_create();
  }

  storeSenderKey(sender: SignalProtocolAddress, distributionId: string, record: SenderKeyRecord): Promise<void> | void {
    senderKeyStoreWasm.senderkeystore_store_sender_key(this.storeHandle, sender.handle, distributionId, record.handle);
  }
  
  loadSenderKey(
    sender: SignalProtocolAddress,
    distributionId: string
  ): SenderKeyRecord | null {

    try {
      const recordHandle = senderKeyStoreWasm.senderkeystore_load_sender_key(this.storeHandle, sender.handle, distributionId);
      // Defensive copy (matches Java behavior exactly)
      return new SenderKeyRecord(recordHandle);
    } catch (e) {
      // Java throws AssertionError — same intent here
      throw new Error(`AssertionError: ${(e as Error).message}`);
    }
  }

  getStoreHandle(): number {
    return this.storeHandle;
  }
}

import { NoSessionException } from "../../exceptions/NoSessionException";
import type { SignalProtocolAddress } from "../../protocol/SignalProtocolAddress";
import { SessionRecord } from "../SessionRecord";
import type { SessionStore } from "../SessionStore";
import { sessionStore as  sessionStoreWasm } from "../../../../../libsignal-protocol-wasm/wasm-wrapper/pkg/libsignal_wasm_pqxdh";


/**
 * In-memory SessionStore keyed by opaque remote address HANDLE (number).
 *
 * IMPORTANT:
 * - WASM passes a numeric handle, NOT a SignalProtocolAddress object
 * - The handle uniquely identifies (store id) on the Rust side
 */
export class InMemorySessionStore implements SessionStore {

  private readonly storeHandle!: number;
  constructor() {
    this.storeHandle = sessionStoreWasm.sessionstore_create_session_store();
  }


  async loadSession(address: SignalProtocolAddress): Promise<SessionRecord | null> {
    const serialized = sessionStoreWasm.sessionstore_load_session(this.storeHandle, address.handle);

    if (!serialized) {
      // Rust expects None, not empty SessionRecord
      return null;
    }

    try {
      return new SessionRecord(serialized);
    } catch (e) {
      throw new Error(`AssertionError: ${(e as Error).message}`);
    }
  }

  loadExistingSessions(addresses: SignalProtocolAddress[]): SessionRecord[] {
    const result: SessionRecord[] = [];

    for (const address of addresses) {
      const serialized = sessionStoreWasm.sessionstore_load_session(this.storeHandle, address.handle);

      if (!serialized) {
        throw new NoSessionException(
          address + 
          `no session for remote handle ${address}`
        );
      }

      try {
        result.push(new SessionRecord(serialized));
      } catch (e) {
        throw new Error(`AssertionError: ${(e as Error).message}`);
      }
    }

    return result;
  }

  async storeSession(address: SignalProtocolAddress,  serialized: Uint8Array): Promise<void> {
    sessionStoreWasm.sessionstore_store_session_record(this.storeHandle, address.handle, serialized);
  }

  containsSession(address: SignalProtocolAddress): boolean {
    return sessionStoreWasm.sessionstore_contains_session(this.storeHandle, address.handle);
  }

  deleteSession(address: SignalProtocolAddress): void {
    sessionStoreWasm.sessionstore_delete_session(this.storeHandle, address.handle);
  }

  getSessionStoreHandle(): number {
    return this.storeHandle;
  }
}

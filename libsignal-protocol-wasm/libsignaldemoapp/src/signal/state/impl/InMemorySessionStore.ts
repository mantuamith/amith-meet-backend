import { NoSessionException } from "../../exceptions/NoSessionException";
import { SessionRecord } from "../SessionRecord";
import type { SessionStore } from "../SessionStore";

/**
 * In-memory SessionStore keyed by opaque remote address HANDLE (number).
 *
 * IMPORTANT:
 * - WASM passes a numeric handle, NOT a SignalProtocolAddress object
 * - The handle uniquely identifies (name, deviceId) on the Rust side
 */
export class InMemorySessionStore implements SessionStore {

  private sessions = new Map<string, Uint8Array>();

  constructor() {}

  /** Helper to map remote address HANDLE → unique string key */
  private addressKey(remoteHandle: number): string {
    return String(remoteHandle);
  }

  async loadSession(remoteHandle: number): Promise<SessionRecord | null> {
    const key = this.addressKey(remoteHandle);
    const serialized = this.sessions.get(key);

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

  loadExistingSessions(remoteHandles: number[]): SessionRecord[] {
    const result: SessionRecord[] = [];

    for (const handle of remoteHandles) {
      const key = this.addressKey(handle);
      const serialized = this.sessions.get(key);

      if (!serialized) {
        throw new NoSessionException(
          handle + 
          `no session for remote handle ${handle}`
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

  async storeSession(remoteHandle: number,  serialized: Uint8Array): Promise<void> {
    const key = this.addressKey(remoteHandle);
    this.sessions.set(key, serialized);
  }

  containsSession(remoteHandle: number): boolean {
    return this.sessions.has(this.addressKey(remoteHandle));
  }

  deleteSession(remoteHandle: number): void {
    this.sessions.delete(this.addressKey(remoteHandle));
  }

  deleteAllSessionsForHandle(remoteHandle: number): void {
    this.sessions.delete(this.addressKey(remoteHandle));
  }
}

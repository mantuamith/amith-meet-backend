import { NoSessionException } from "../../exceptions/NoSessionException";
import { SessionRecord } from "../SessionRecord";
import type { SignalProtocolAddress } from "../../protocol/SignalProtocolAddress";
import type { SessionStore_old } from "../SessionStore_old";

export class InMemorySessionStore_old implements SessionStore_old {

  private sessions = new Map<string, Uint8Array>();

  constructor() {}

  /** Helper to map SignalProtocolAddress → unique string key */
  private addressKey(address: SignalProtocolAddress): string {
    return `${address.getName()}:${address.getDeviceId()}`;
  }

  async loadSession(
      remoteAddress: SignalProtocolAddress
    ): Promise<SessionRecord | null> {
      const key = this.addressKey(remoteAddress);
      const serialized = this.sessions.get(key);

      if (!serialized) {
        // IMPORTANT:
        // libsignal Rust expects `None`, not an empty SessionRecord
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
      const key = this.addressKey(address);
      const serialized = this.sessions.get(key);

      if (!serialized) {
        throw new NoSessionException(address, `no session for ${address.toString()}`);
      }

      try {
        result.push(new SessionRecord(serialized));
      } catch (e) {
        throw new Error(`AssertionError: ${(e as Error).message}`);
      }
    }

    return result;
  }

  getSubDeviceSessions(name: string): number[] {
    const result: number[] = [];

    for (const [key, _value] of this.sessions.entries()) {
      const [storedName, deviceIdStr] = key.split(":");
      const deviceId = Number(deviceIdStr);

      if (storedName === name && deviceId !== 1) {
        result.push(deviceId);
      }
    }

    return result;
  }

  async storeSession(
    address: SignalProtocolAddress,
    record: SessionRecord
  ): Promise<void> {
    const key = this.addressKey(address);
    this.sessions.set(key, record.serialize());
  }

  containsSession(address: SignalProtocolAddress): boolean {
    return this.sessions.has(this.addressKey(address));
  }

  deleteSession(address: SignalProtocolAddress): void {
    this.sessions.delete(this.addressKey(address));
  }

  deleteAllSessions(name: string): void {
    for (const key of [...this.sessions.keys()]) {
      const [storedName] = key.split(":");
      if (storedName === name) {
        this.sessions.delete(key);
      }
    }
  }
}
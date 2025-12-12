import { NoSessionException } from "../../exceptions/NoSessionException";
import { SessionRecord } from "../SessionRecord";
import type { SessionStore } from "../SessionStore";
import type { SignalProtocolAddress } from "../../protocol/SignalProtocolAddress";

export class InMemorySessionStore implements SessionStore {

  private sessions = new Map<string, Uint8Array>();

  constructor() {}

  /** Helper to map SignalProtocolAddress → unique string key */
  private addressKey(address: SignalProtocolAddress): string {
    return `${address.name}:${address.deviceId}`;
  }

  loadSession(remoteAddress: SignalProtocolAddress): SessionRecord {
    const key = this.addressKey(remoteAddress);
    const serialized = this.sessions.get(key);

    if (!serialized) {
      return new SessionRecord(); // matches Java behavior
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

  storeSession(address: SignalProtocolAddress, record: SessionRecord): void {
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
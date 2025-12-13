import { InvalidKeyIdException } from "../../exceptions/InvalidKeyIdException";
import { SignedPreKeyRecord } from "../SignedPreKeyRecord";
import type { SignedPreKeyStore } from "../SignedPreKeyStore";

export class InMemorySignedPreKeyStore implements SignedPreKeyStore {

  private store = new Map<number, Uint8Array>();

  constructor() {}

  async loadSignedPreKey(id: number): Promise<SignedPreKeyRecord> {
    const serialized = this.store.get(id);

    if (!serialized) {
      throw new InvalidKeyIdException(`No such SignedPreKeyRecord! ${id}`);
    }

    try {
      return new SignedPreKeyRecord(serialized);
    } catch (e) {
      throw new Error(`AssertionError: ${(e as Error).message}`);
    }
  }

  loadSignedPreKeys(): SignedPreKeyRecord[] {
    const results: SignedPreKeyRecord[] = [];

    try {
      for (const serialized of this.store.values()) {
        results.push(new SignedPreKeyRecord(serialized));
      }
    } catch (e) {
      throw new Error(`AssertionError: ${(e as Error).message}`);
    }

    return results;
  }

  async storeSignedPreKey(id: number, record: SignedPreKeyRecord): Promise<void> {
    this.store.set(id, record.serialize());
  }

  containsSignedPreKey(id: number): boolean {
    return this.store.has(id);
  }

  removeSignedPreKey(id: number): void {
    this.store.delete(id);
  }
}
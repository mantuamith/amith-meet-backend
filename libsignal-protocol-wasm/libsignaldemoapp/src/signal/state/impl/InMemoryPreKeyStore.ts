import { InvalidKeyIdException } from "../../exceptions/InvalidKeyIdException";
import { PreKeyRecord } from "../PreKeyRecord";
import type { PreKeyStore } from "../PreKeyStore";

export class InMemoryPreKeyStore implements PreKeyStore {

  private store: Map<number, Uint8Array> = new Map();

  async loadPreKey(preKeyId: number): Promise<PreKeyRecord> {
    if (!this.store.has(preKeyId)) {
      throw new InvalidKeyIdException("No such prekeyrecord!");
    }

    try {
      const raw = this.store.get(preKeyId)!;
      return new PreKeyRecord(raw);
    } catch (e) {
      throw new Error(`AssertionError: ${(e as Error).message}`);
    }
  }

  async storePreKey(preKeyId: number, record: PreKeyRecord): Promise<void> {
    this.store.set(preKeyId, record.serialize());
  }

  containsPreKey(preKeyId: number): boolean {
    return this.store.has(preKeyId);
  }

  async removePreKey(preKeyId: number): Promise<void> {
    this.store.delete(preKeyId);
  }
}
import { InvalidKeyIdException } from "../../exceptions/InvalidKeyIdException";
import { PreKeyRecord } from "../PreKeyRecord";
import type { PreKeyStore } from "../PreKeyStore";

export class InMemoryPreKeyStore implements PreKeyStore {

  private store: Map<number, Uint8Array> = new Map();

  loadPreKey(preKeyId: number): PreKeyRecord {
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

  storePreKey(preKeyId: number, record: PreKeyRecord): void {
    this.store.set(preKeyId, record.serialize());
  }

  containsPreKey(preKeyId: number): boolean {
    return this.store.has(preKeyId);
  }

  removePreKey(preKeyId: number): void {
    this.store.delete(preKeyId);
  }
}
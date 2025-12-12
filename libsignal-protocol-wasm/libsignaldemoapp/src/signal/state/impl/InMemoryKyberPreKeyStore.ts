import { InvalidKeyIdException } from "../../exceptions/InvalidKeyIdException";
import { ReusedBaseKeyException } from "../../exceptions/ReusedBaseKeyException";
import type { ECPublicKey } from "../../protocol/ecc/ECPublicKey";
import { KyberPreKeyRecord } from "../KyberPreKeyRecord";
import type { KyberPreKeyStore } from "../KyberPreKeyStore";

export type KyberTuple = readonly [number, number];

function makeTuple(a: number, b: number): KyberTuple {
  return [a, b] as const;
}

export class InMemoryKyberPreKeyStore implements KyberPreKeyStore {

  private store = new Map<number, Uint8Array>();
  private used = new Set<number>();

  /**
   * (kyberPreKeyId, signedPreKeyId)  →  Set<ECPublicKey>
   */
  private baseKeysSeen = new Map<KyberTuple, Set<ECPublicKey>>();

  // ---------------------------------------------------------------------------
  // loadKyberPreKey()
  // ---------------------------------------------------------------------------

  loadKyberPreKey(id: number): KyberPreKeyRecord {
    const serialized = this.store.get(id);

    if (!serialized) {
      throw new InvalidKeyIdException(`No such KyberPreKeyRecord! ${id}`);
    }

    try {
      return new KyberPreKeyRecord(serialized);
    } catch (e) {
      throw new Error(`AssertionError: ${(e as Error).message}`);
    }
  }

  // ---------------------------------------------------------------------------
  // loadKyberPreKeys()
  // ---------------------------------------------------------------------------

  loadKyberPreKeys(): KyberPreKeyRecord[] {
    const results: KyberPreKeyRecord[] = [];

    for (const serialized of this.store.values()) {
      try {
        results.push(new KyberPreKeyRecord(serialized));
      } catch (e) {
        throw new Error(`AssertionError: ${(e as Error).message}`);
      }
    }

    return results;
  }

  // ---------------------------------------------------------------------------
  // storeKyberPreKey()
  // ---------------------------------------------------------------------------

  storeKyberPreKey(id: number, record: KyberPreKeyRecord): void {
    this.store.set(id, record.serialize());
  }

  // ---------------------------------------------------------------------------
  // containsKyberPreKey()
  // ---------------------------------------------------------------------------

  containsKyberPreKey(id: number): boolean {
    return this.store.has(id);
  }

  // ---------------------------------------------------------------------------
  // markKyberPreKeyUsed()
  // ---------------------------------------------------------------------------

  markKyberPreKeyUsed(
    kyberPreKeyId: number,
    signedPreKeyId: number,
    baseKey: ECPublicKey
  ): void {

    this.used.add(kyberPreKeyId);

    const tuple = makeTuple(kyberPreKeyId, signedPreKeyId);
    const seenSet = this.baseKeysSeen.get(tuple);

    if (!seenSet) {
      // First time this (kyberPreKeyId, signedPreKeyId) pair is used
      this.baseKeysSeen.set(tuple, new Set([baseKey]));
      return;
    }

    // Check whether this exact ECPublicKey was already used
    //
    // NOTE: relies on your ECPublicKey.equals(other) implementation.
    for (const existing of seenSet) {
      if (existing.equals(baseKey)) {
        throw new ReusedBaseKeyException();
      }
    }

    seenSet.add(baseKey);
  }

  // ---------------------------------------------------------------------------
  // hasKyberPreKeyBeenUsed()
  // ---------------------------------------------------------------------------

  hasKyberPreKeyBeenUsed(id: number): boolean {
    return this.used.has(id);
  }
}
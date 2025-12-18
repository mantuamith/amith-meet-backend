import type { ECPublicKey } from "../protocol/ecc/ECPublicKey";
import type { KyberPreKeyRecord } from "./KyberPreKeyRecord";

export interface KyberPreKeyStore {

  /**
   * Load a local KyberPreKeyRecord.
   *
   * @throws InvalidKeyIdException if no such pre-key exists.
   */
  loadKyberPreKey(kyberPreKeyId: number): KyberPreKeyRecord;

  /**
   * Load all stored KyberPreKeyRecords.
   */
  loadKyberPreKeys(): KyberPreKeyRecord[];

  /**
   * Store a KyberPreKeyRecord.
   */
  storeKyberPreKey(kyberPreKeyId: number, record: KyberPreKeyRecord): void;

  /**
   * Check whether a KyberPreKeyRecord exists for the given ID.
   */
  containsKyberPreKey(kyberPreKeyId: number): boolean;

  /**
   * Mark a KyberPreKeyRecord as used.
   *
   * - Track use of last-resort keys.
   * - Detect replay of the same (kyberPreKeyId, signedPreKeyId, baseKey) tuple.
   *
   * @throws ReusedBaseKeyException if the same tuple was seen previously.
   */
  markKyberPreKeyUsed(
    kyberPreKeyId: number,
    signedPreKeyId: number,
    baseKey: ECPublicKey
  ): void;

  getKyberPreKeyStoreHandle(): number;
}
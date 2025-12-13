import type { SignedPreKeyRecord } from "./SignedPreKeyRecord";

export interface SignedPreKeyStore {

  /**
   * Load a local SignedPreKeyRecord.
   *
   * @throws InvalidKeyIdException if no record exists for the given id.
   */
  loadSignedPreKey(signedPreKeyId: number): Promise<SignedPreKeyRecord>;

  /**
   * Load all stored SignedPreKeyRecords.
   */
  loadSignedPreKeys(): SignedPreKeyRecord[];

  /**
   * Store a SignedPreKeyRecord.
   */
  storeSignedPreKey(signedPreKeyId: number, record: SignedPreKeyRecord): Promise<void>;

  /**
   * Check whether a SignedPreKeyRecord exists for the given id.
   */
  containsSignedPreKey(signedPreKeyId: number): boolean;

  /**
   * Remove a SignedPreKeyRecord for the given id.
   */
  removeSignedPreKey(signedPreKeyId: number): void;
}
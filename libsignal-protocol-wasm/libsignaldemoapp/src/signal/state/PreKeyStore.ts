import type { PreKeyRecord } from "./PreKeyRecord";

export interface PreKeyStore {

  /**
   * Load a local PreKeyRecord.
   *
   * @param preKeyId - The ID of the PreKeyRecord to load.
   * @throws InvalidKeyIdException if there is no corresponding record.
   */
  loadPreKey(preKeyId: number): Promise<PreKeyRecord>;

  /**
   * Store a local PreKeyRecord.
   *
   * @param preKeyId - The ID of the PreKeyRecord to store.
   * @param record - The PreKeyRecord instance.
   */
  storePreKey(preKeyId: number, record: PreKeyRecord): Promise<void>;

  /**
   * Check whether a PreKeyRecord exists for the given ID.
   *
   * @param preKeyId - PreKeyRecord ID.
   * @returns true if found, false otherwise.
   */
  containsPreKey(preKeyId: number): boolean;

  /**
   * Delete a stored PreKeyRecord.
   *
   * @param preKeyId - The ID of the PreKeyRecord to remove.
   */
  removePreKey(preKeyId: number): Promise<void>;
}
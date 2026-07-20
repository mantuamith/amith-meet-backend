/**
 * WASM bindings for SenderKeyRecord operations.
 * Must correspond to:
 *   - SenderKeyRecord_Deserialize
 *   - SenderKeyRecord_GetSerialized
 *   - SenderKeyRecord_Destroy
 */
export interface SenderKeyRecordWasm {
  /**
   * Deserialize a SenderKeyRecord from bytes.
   *
   * Corresponds to:
   *   SenderKeyRecord_Deserialize
   *
   * @param bytes Serialized SenderKeyRecord
   * @returns Pointer to native SenderKeyRecord
   */
  senderkeyrecord_deserialize(bytes: Uint8Array): number;

  /**
   * Serialize a SenderKeyRecord.
   *
   * Corresponds to:
   *   SenderKeyRecord_GetSerialized
   *
   * @param ptr Native SenderKeyRecord pointer
   * @returns Serialized SenderKeyRecord bytes
   */
  senderkeyrecord_serialize(ptr: number): Uint8Array;

  /**
   * Destroy a SenderKeyRecord.
   *
   * Corresponds to:
   *   SenderKeyRecord_Destroy
   *
   * @param ptr Native SenderKeyRecord pointer
   */
  senderkeyrecord_destroy(ptr: number): void;
}

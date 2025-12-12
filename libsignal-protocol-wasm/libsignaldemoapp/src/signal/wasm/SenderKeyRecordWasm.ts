export interface SenderKeyRecordWasm {
  senderkeyrecord_deserialize(serialized: Uint8Array): number;
  senderkeyrecord_get_serialized(ptr: number): Uint8Array;
  senderkeyrecord_destroy(ptr: number): void;
}
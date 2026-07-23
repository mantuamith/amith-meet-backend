export interface PreKeyRecordWasm {
  prekeyrecord_new(id: number, pubPtr: number, privPtr: number): number;
  prekeyrecord_deserialize(data: Uint8Array): number;

  prekeyrecord_get_id(ptr: number): number;

  prekeyrecord_get_public_key(ptr: number): number;
  prekeyrecord_get_private_key(ptr: number): number;

  prekeyrecord_get_serialized(ptr: number): Uint8Array;

  prekeyrecord_destroy(ptr: number): void;
}
export interface KyberPreKeyRecordWasm {
  kyber_prekey_new(
    id: number,
    timestamp: number,
    keyPairPtr: number,
    signature: Uint8Array
  ): number;

  kyber_prekey_deserialize(data: Uint8Array): number;

  kyber_prekey_get_id(ptr: number): number;
  kyber_prekey_get_timestamp(ptr: number): number;
  kyber_prekey_get_keypair(ptr: number): number;

  kyber_prekey_get_signature(ptr: number): Uint8Array;
  kyber_prekey_serialize(ptr: number): Uint8Array;

  kyber_prekey_destroy(ptr: number): void;
}

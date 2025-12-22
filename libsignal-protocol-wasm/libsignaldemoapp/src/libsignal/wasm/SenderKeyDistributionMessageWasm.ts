export interface SenderKeyDistributionMessageWasm {
  senderkeydistributionmessage_deserialize(
    bytes: Uint8Array
  ): number;

  senderkeydistributionmessage_destroy(
    ptr: number
  ): void;

  senderkeydistributionmessage_get_serialized(
    ptr: number
  ): Uint8Array;

  senderkeydistributionmessage_get_distribution_id(
    ptr: number
  ): string;

  senderkeydistributionmessage_get_iteration(
    ptr: number
  ): number;

  senderkeydistributionmessage_get_chain_key(
    ptr: number
  ): Uint8Array;

  senderkeydistributionmessage_get_signature_key(
    ptr: number
  ): number;

  senderkeydistributionmessage_get_chain_id(
    ptr: number
  ): number;
}

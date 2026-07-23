export interface SignalMessageWasm {
  signalmessage_deserialize(serialized: Uint8Array): number;
  signalmessage_destroy(handle: number): void;

  signalmessage_get_sender_ratchet_key(handle: number): number;
  signalmessage_get_message_version(handle: number): number;
  signalmessage_get_counter(handle: number): number;
  signalmessage_get_body(handle: number): Uint8Array;
  signalmessage_get_pq_ratchet(handle: number): Uint8Array;

  signalmessage_verify_mac(
    handle: number,
    senderIdentityHandle: number,
    receiverIdentityHandle: number,
    macKey: Uint8Array
  ): boolean;

  signalmessage_get_serialized(handle: number): Uint8Array;
}

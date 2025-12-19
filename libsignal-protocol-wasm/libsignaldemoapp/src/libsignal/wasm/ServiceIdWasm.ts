export interface ServiceIdWasm {
  /**
   * Parse a Service ID from its string form (e.g., "PNI:...").
   * Returns the 17-byte fixed-width representation.
   */
  serviceid_parse_from_service_id_string(str: string): Uint8Array;

  /**
   * Parse a Service ID from its binary form.
   * Returns the 17-byte fixed-width representation.
   */
  serviceid_parse_from_service_id_binary(bytes: Uint8Array): Uint8Array;

  /**
   * Return a human-readable service ID (used for logs).
   */
  serviceid_log(storage: Uint8Array): string;

  /**
   * Convert fixed-width binary storage → canonical binary representation.
   */
  serviceid_binary(storage: Uint8Array): Uint8Array;

  /**
   * Convert fixed-width storage → canonical string representation.
   */
  serviceid_string(storage: Uint8Array): string;
}

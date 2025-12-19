export interface SignalProtocolAddressWasm {
  /**
   * Create a new Signal protocol address.
   * Returns a numeric handle (pointer).
   *
   * Rust: protocoladdress_new(name, device_id) -> u32
   */
  protocoladdress_new(name: string, device_id: number): number;

  /**
   * Destroy/free an allocated ProtocolAddress.
   *
   * Rust: protocoladdress_destroy(ptr)
   */
  protocoladdress_destroy(ptr: number): void;

  /**
   * Read the "name" field of the SignalProtocolAddress.
   *
   * Rust: protocoladdress_name(ptr) -> String
   */
  protocoladdress_name(ptr: number): string;

  /**
   * Read the device ID (1–127).
   *
   * Rust: protocoladdress_device_id(ptr) -> u32
   */
  protocoladdress_device_id(ptr: number): number;
}

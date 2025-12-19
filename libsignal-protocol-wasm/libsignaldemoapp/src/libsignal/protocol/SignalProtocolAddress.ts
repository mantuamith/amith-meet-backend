import { ServiceId } from "./ServiceId";
import { protocolAddress as  protocolAddressWasm } from "../../../../libsignal-protocol-wasm/wasm-wrapper/pkg/libsignal_wasm_pqxdh";

/**
 * TypeScript equivalent of SignalProtocolAddress.
 * Manages a native pointer and exposes name + deviceId.
 */
export class SignalProtocolAddress {
  /** Native pointer (WASM handle) */
  public readonly handle: number;

  // ----------------------------------------------------------
  // Constructors
  // ----------------------------------------------------------

  /**
   * new SignalProtocolAddress("user123", 1)
   */
  constructor(name: string, deviceId: number);

  /**
   * new SignalProtocolAddress(serviceId, 1)
   */
  constructor(serviceId: ServiceId, deviceId: number);

  /**
   * new SignalProtocolAddress(nativePtr)
   */
  constructor(nativeHandle: number);

  constructor(nameOrService: string | ServiceId | number, deviceId?: number) {
    // Case 1: Construct from native handle
    if (typeof nameOrService === "number" && deviceId === undefined) {
      this.handle = nameOrService;
      return;
    }

    // Normalize to name string
    let name: string;

    if (typeof nameOrService === "string") {
      name = nameOrService;
    } else if (nameOrService instanceof ServiceId) {
      name = nameOrService.toServiceIdString();
    } else {
      throw new Error("Invalid constructor parameters for SignalProtocolAddress");
    }

    if (typeof deviceId !== "number" || deviceId < 1 || deviceId > 127) {
      throw new Error("deviceId must be between 1 and 127");
    }

    // Call WASM constructor
    const ptr = protocolAddressWasm.protocoladdress_new(name, deviceId);
    if (!ptr) {
      throw new Error("Failed to allocate native SignalProtocolAddress");
    }

    this.handle = ptr;
  }

  // ----------------------------------------------------------
  // Destructor
  // ----------------------------------------------------------
  destroy(): void {
    protocolAddressWasm.protocoladdress_destroy(this.handle);
  }

  // ----------------------------------------------------------
  // Getters
  // ----------------------------------------------------------

  getName(): string {
    return protocolAddressWasm.protocoladdress_name(this.handle);
  }

  getServiceId(): ServiceId | null {
    try {
      return ServiceId.parseFromString(this.getName());
    } catch {
      return null;
    }
  }

  getDeviceId(): number {
    return protocolAddressWasm.protocoladdress_device_id(this.handle);
  }

  // ----------------------------------------------------------
  // Equality, Hashing, toString
  // ----------------------------------------------------------

  toString(): string {
    return `${this.getName()}.${this.getDeviceId()}`;
  }

  equals(other: unknown): boolean {
    if (!(other instanceof SignalProtocolAddress)) return false;
    return (
      this.getName() === other.getName() &&
      this.getDeviceId() === other.getDeviceId()
    );
  }

  hashCode(): number {
    const nameHash = this.stringHash(this.getName());
    return nameHash ^ this.getDeviceId();
  }

  private stringHash(str: string): number {
    let h = 0;
    for (let i = 0; i < str.length; i++) {
      h = (h << 5) - h + str.charCodeAt(i);
      h |= 0;
    }
    return h;
  }

  // ----------------------------------------------------------
  // Allow WASM internals to fetch the raw pointer
  // ----------------------------------------------------------
  get nativeHandle(): number {
    return this.handle;
  }
}

import type { ServiceIdWasm } from "../wasm/ServiceIdWasm";

/** Java-equivalent exception */
declare const wasm: ServiceIdWasm;

export class InvalidServiceIdException extends Error {
  constructor(message = "Invalid Service ID") {
    super(message);
  }
}

export abstract class ServiceId implements Comparable<ServiceId> {
  static readonly FIXED_WIDTH_BINARY_LENGTH = 17;

  static readonly ACI_MARKER = 0x00;
  static readonly PNI_MARKER = 0x01;

  protected storage: Uint8Array;

  protected constructor(storage: Uint8Array) {
    if (!storage || storage.length !== ServiceId.FIXED_WIDTH_BINARY_LENGTH) {
      throw new InvalidServiceIdException("Invalid fixed-width storage");
    }
    this.storage = storage;
  }

  // ------------------------------------------------------------
  // Constructors for subclasses
  // ------------------------------------------------------------

  protected static buildStorage(marker: number, uuid: UUID): Uint8Array {
    const out = new Uint8Array(17);
    const view = new DataView(out.buffer);

    out[0] = marker;

    // Write UUID MSB + LSB into bytes [1..16]
    view.setBigUint64(1, uuid.mostSignificantBits);
    view.setBigUint64(9, uuid.leastSignificantBits);

    return out;
  }

  // ------------------------------------------------------------
  // Equality / Ordering
  // ------------------------------------------------------------

  equals(other: unknown): boolean {
    return (
      other instanceof ServiceId &&
      this.storage.length === other.storage.length &&
      this.storage.every((b, i) => other.storage[i] === b)
    );
  }

  hashCode(): number {
    let h = 1;
    for (const b of this.storage) h = ((h << 5) - h) ^ b;
    return h | 0;
  }

  compareTo(other: ServiceId): number {
    for (let i = 0; i < ServiceId.FIXED_WIDTH_BINARY_LENGTH; i++) {
      const a = this.storage[i] & 0xff;
      const b = other.storage[i] & 0xff;
      if (a !== b) return a - b;
    }
    return 0;
  }

  toString(): string {
    return this.toLogString();
  }

  // ------------------------------------------------------------
  // Public API (UPDATED to new wasm function names)
  // ------------------------------------------------------------

  getKind(): "ACI" | "PNI" {
    return this.storage[0] === ServiceId.ACI_MARKER ? "ACI" : "PNI";
  }

  getRawUUID(): UUID {
    const view = new DataView(this.storage.buffer, 1);
    const msb = view.getBigUint64(0);
    const lsb = view.getBigUint64(8);
    return new UUID(msb, lsb);
  }

  toServiceIdString(): string {
    return wasm.serviceid_string(this.storage);
  }

  toServiceIdBinary(): Uint8Array {
    return wasm.serviceid_binary(this.storage);
  }

  toServiceIdFixedWidthBinary(): Uint8Array {
    return new Uint8Array(this.storage);
  }

  toLogString(): string {
    return wasm.serviceid_log(this.storage);
  }

  // ------------------------------------------------------------
  // Static parse methods (UPDATED)
  // ------------------------------------------------------------

  static parseFromString(serviceIdString: string): ServiceId {
    if (!serviceIdString) throw new InvalidServiceIdException("Input cannot be null");

    let storage: Uint8Array;
    try {
      storage = wasm.serviceid_parse_from_service_id_string(serviceIdString);
    } catch {
      throw new InvalidServiceIdException();
    }

    return ServiceId.parseFromFixedWidthBinary(storage);
  }

  static parseFromBinary(serviceIdBinary: Uint8Array): ServiceId {
    if (!serviceIdBinary) throw new InvalidServiceIdException();

    let storage: Uint8Array;
    try {
      storage = wasm.serviceid_parse_from_service_id_binary(serviceIdBinary);
    } catch {
      throw new InvalidServiceIdException();
    }

    return ServiceId.parseFromFixedWidthBinary(storage);
  }

  static parseFromFixedWidthBinary(storage: Uint8Array): ServiceId {
    if (!storage || storage.length !== ServiceId.FIXED_WIDTH_BINARY_LENGTH) {
      throw new InvalidServiceIdException();
    }
    switch (storage[0]) {
      case ServiceId.ACI_MARKER:
        return new Aci(storage);
      case ServiceId.PNI_MARKER:
        return new Pni(storage);
      default:
        throw new InvalidServiceIdException();
    }
  }

  static toConcatenatedFixedWidthBinary(ids: ServiceId[]): Uint8Array {
    const out = new Uint8Array(ids.length * ServiceId.FIXED_WIDTH_BINARY_LENGTH);
    let offset = 0;
    for (const id of ids) {
      out.set(id.storage, offset);
      offset += ServiceId.FIXED_WIDTH_BINARY_LENGTH;
    }
    return out;
  }
}

// ------------------------------------------------------------
// Subclasses
// ------------------------------------------------------------

export class Aci extends ServiceId {
  constructor(uuidOrStorage: UUID | Uint8Array) {
    if (uuidOrStorage instanceof Uint8Array) {
      super(uuidOrStorage);
    } else {
      super(ServiceId.buildStorage(ServiceId.ACI_MARKER, uuidOrStorage));
    }
  }
}

export class Pni extends ServiceId {
  constructor(uuidOrStorage: UUID | Uint8Array) {
    if (uuidOrStorage instanceof Uint8Array) {
      super(uuidOrStorage);
    } else {
      super(ServiceId.buildStorage(ServiceId.PNI_MARKER, uuidOrStorage));
    }
  }
}

// ------------------------------------------------------------
// UUID helper
// ------------------------------------------------------------

export class UUID {
  public readonly mostSignificantBits: bigint;
  public readonly leastSignificantBits: bigint;

  constructor(msb: bigint, lsb: bigint) {
    this.mostSignificantBits = msb;
    this.leastSignificantBits = lsb;
  }

  static fromString(str: string): UUID {
    const hex = str.replace(/-/g, "");
    if (hex.length !== 32) {
      throw new Error("Invalid UUID string");
    }

    const msb = BigInt("0x" + hex.slice(0, 16));
    const lsb = BigInt("0x" + hex.slice(16));

    return new UUID(msb, lsb);
  }

  toString(): string {
    const hex =
      this.mostSignificantBits.toString(16).padStart(16, "0") +
      this.leastSignificantBits.toString(16).padStart(16, "0");

    return (
      hex.slice(0, 8) +
      "-" +
      hex.slice(8, 12) +
      "-" +
      hex.slice(12, 16) +
      "-" +
      hex.slice(16, 20) +
      "-" +
      hex.slice(20)
    );
  }
}


export interface Comparable<T> {
  compareTo(other: T): number;
}

import { BaseCiphertextMessage } from "./BaseCiphertextMessage";
import type { CiphertextMessage } from "./CiphertextMessage";

import { SignalMessage } from "./SignalMessage";
import { IdentityKey } from "../IdentityKey";
import { ECPublicKey } from "../ecc/ECPublicKey";

import {preKeySignalMessage as preKeySignalMessageWasm} from "libsignal_wasm_pqxdh";

/**
 * TypeScript equivalent of Java's PreKeySignalMessage.
 *
 * Wraps a native WASM handle and mirrors NativeHandleGuard.SimpleOwner.
 */
export class PreKeySignalMessage
  extends BaseCiphertextMessage
  implements CiphertextMessage
{
  /** Native pointer */
  readonly handle: number;

  // --------------------------------------------------
  // Constructors
  // --------------------------------------------------

  constructor(serialized: Uint8Array);
  constructor(nativeHandle: number);
  constructor(arg: Uint8Array | number) {
    let handle: number;

    if (typeof arg === "number") {
      // CalledFromNative path
      handle = arg;
    } else {
      // Deserialize path
      handle = preKeySignalMessageWasm.prekeysignalmessage_deserialize(arg);
      if (!handle) {
        throw new Error("Failed to deserialize PreKeySignalMessage");
      }
    }

    super(handle);
    this.handle = handle;
  }

  // --------------------------------------------------
  // Native lifecycle
  // --------------------------------------------------

  protected destroy(): void {
    preKeySignalMessageWasm.prekeysignalmessage_destroy(this.handle);
  }

  // --------------------------------------------------
  // Accessors (Java method equivalents)
  // --------------------------------------------------

  getMessageVersion(): number {
    return preKeySignalMessageWasm.prekeysignalmessage_get_version(this.handle);
  }

  getIdentityKey(): IdentityKey {
    const ptr = preKeySignalMessageWasm.prekeysignalmessage_get_identity_key(this.handle);
    return new IdentityKey(ptr);
  }

  getRegistrationId(): number {
    return preKeySignalMessageWasm.prekeysignalmessage_get_registration_id(this.handle);
  }

  getPreKeyId(): number | null {
    const id = preKeySignalMessageWasm.prekeysignalmessage_get_pre_key_id(this.handle);
    return id < 0 ? null : id;
  }

  getSignedPreKeyId(): number {
    return preKeySignalMessageWasm.prekeysignalmessage_get_signed_pre_key_id(this.handle);
  }

  getBaseKey(): ECPublicKey {
    const ptr = preKeySignalMessageWasm.prekeysignalmessage_get_base_key(this.handle);
    return new ECPublicKey(ptr);
  }

  getWhisperMessage(): SignalMessage {
    const ptr = preKeySignalMessageWasm.prekeysignalmessage_get_signal_message(this.handle);
    return new SignalMessage(ptr);
  }

  // --------------------------------------------------
  // CiphertextMessage
  // --------------------------------------------------

  serialize(): Uint8Array {
    return preKeySignalMessageWasm.prekeysignalmessage_serialize(this.handle);
  }

  getType(): number {
    return BaseCiphertextMessage.PREKEY_TYPE;
  }
}

import{
    CiphertextMessageConstants,
    type CiphertextMessage,
} from "./CiphertextMessage";

import { IdentityKey } from "../IdentityKey";
import { ECPublicKey } from "../ecc/ECPublicKey";
import { InvalidMessageException } from "../../exceptions/InvalidMessageException";
import { signalMessage as signalMessageWasm} from "../../../../../libsignal-protocol-wasm/wasm-wrapper/pkg/libsignal_wasm_pqxdh";


export interface NativeHandleOwner {
  readonly handle: number;
  destroy(): void;
}

export class SignalMessage
  implements CiphertextMessage, NativeHandleOwner
{
  readonly handle: number;

  // Deserialize constructor
  constructor(serialized: Uint8Array);
  // Native-handle constructor
  constructor(nativeHandle: number);
  constructor(arg: Uint8Array | number) {
    if (typeof arg === "number") {
      this.handle = arg;
    } else {
      try {
        this.handle = signalMessageWasm.signalmessage_deserialize(arg);
      } catch (e) {
        throw new InvalidMessageException(e + " Invalid message");
      }
    }
  }

  destroy(): void {
    signalMessageWasm.signalmessage_destroy(this.handle);
  }

  getSenderRatchetKey(): ECPublicKey {
    const pkHandle =
      signalMessageWasm.signalmessage_get_sender_ratchet_key(this.handle);
    return new ECPublicKey(pkHandle);
  }

  getMessageVersion(): number {
    return signalMessageWasm.signalmessage_get_message_version(this.handle);
  }

  getCounter(): number {
    return signalMessageWasm.signalmessage_get_counter(this.handle);
  }

  getBody(): Uint8Array {
    return signalMessageWasm.signalmessage_get_body(this.handle);
  }

  getPqRatchet(): Uint8Array {
    return signalMessageWasm.signalmessage_get_pq_ratchet(this.handle);
  }

  verifyMac(
    senderIdentityKey: IdentityKey,
    receiverIdentityKey: IdentityKey,
    macKey: Uint8Array
  ): void {
    const ok = signalMessageWasm.signalmessage_verify_mac(
      this.handle,
      senderIdentityKey.getPublicKey().handle,
      receiverIdentityKey.getPublicKey().handle,
      macKey
    );

    if (!ok) {
      throw new InvalidMessageException("Bad MAC");
    }
  }

  serialize(): Uint8Array {
    return signalMessageWasm.signalmessage_get_serialized(this.handle);
  }

  getType(): number {
    return CiphertextMessageConstants.WHISPER_TYPE;
  }

  static isLegacy(message: Uint8Array | null): boolean {
    if (!message || message.length < 1) return false;
    return (
      (message[0] >> 4) !== CiphertextMessageConstants.CURRENT_VERSION
    );
  }
}

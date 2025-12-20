import {senderKeyDistributionMessage as senderKeyDistributionMessageWasm} from "../../../../../libsignal-protocol-wasm/wasm-wrapper/pkg/libsignal_wasm_pqxdh";
import { ECPublicKey } from "../ecc/ECPublicKey";


/**
 * Equivalent of:
 * org.signal.libsignal.protocol.message.SenderKeyDistributionMessage
 *
 * WASM-backed, handle-owning object.
 */
export class SenderKeyDistributionMessage {
  /** Native pointer */
  readonly handle: number;
  
  /**
   * Construct from an existing native handle.
   */
  constructor(nativeHandle: number);

  /**
   * Deserialize from bytes.
   *
   * Throws:
   * - InvalidMessageException
   * - InvalidVersionException
   * - LegacyMessageException
   * - InvalidKeyException
   */
  constructor(serialized: Uint8Array);

  constructor(arg: number | Uint8Array) {
    if (typeof arg === "number") {
      this.handle = arg;
    } else {
           this.handle = senderKeyDistributionMessageWasm.senderkeydistributionmessage_deserialize(arg);
    }
  }

  /**
   * Release native resources.
   */
  protected release(nativeHandle: number): void {
    senderKeyDistributionMessageWasm.senderkeydistributionmessage_destroy(nativeHandle);
  }

  /**
   * Serialize this message.
   */
  serialize(): Uint8Array {
    return senderKeyDistributionMessageWasm.senderkeydistributionmessage_get_serialized(this.handle);
  }

  /**
   * Distribution ID (UUID string).
   */
  getDistributionId(): string {
    return senderKeyDistributionMessageWasm.senderkeydistributionmessage_get_distribution_id(this.handle);
  }

  /**
   * Chain iteration.
   */
  getIteration(): number {
    return senderKeyDistributionMessageWasm.senderkeydistributionmessage_get_iteration(this.handle);
  }

  /**
   * Sender chain key bytes.
   */
  getChainKey(): Uint8Array {
    return senderKeyDistributionMessageWasm.senderkeydistributionmessage_get_chain_key(this.handle);
  }

  /**
   * Sender signing public key.
   */
  getSignatureKey(): ECPublicKey {
    const ptr = senderKeyDistributionMessageWasm.senderkeydistributionmessage_get_signature_key(this.handle);
    return new ECPublicKey(ptr);
  }

  /**
   * Chain ID.
   */
  getChainId(): number {
    return senderKeyDistributionMessageWasm.senderkeydistributionmessage_get_chain_id(this.handle);
  }
}

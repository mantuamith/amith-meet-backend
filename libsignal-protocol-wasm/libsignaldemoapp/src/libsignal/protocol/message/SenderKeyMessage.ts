// Copyright 2014-2016 Signal Messenger, LLC.
// SPDX-License-Identifier: AGPL-3.0-only

import {senderKeyMessage as senderKeyMessageWasm} 
from "../../../../../libsignal-protocol-wasm/wasm-wrapper/pkg/libsignal_wasm_pqxdh";
import { InvalidMessageException } from "../../exceptions/InvalidMessageException";
import type { ECPublicKey } from "../ecc/ECPublicKey";
import { CiphertextMessageConstants, type CiphertextMessage } from "./CiphertextMessage";

/**
 * SenderKeyMessage represents a group SenderKey-encrypted message.
 *
 * Backed by a native libsignal SenderKeyMessage.
 */
export class SenderKeyMessage implements CiphertextMessage
{
  /** Native pointer */
  readonly handle: number;
  /**
   * Called from native code.
   */
  constructor(nativeHandle: number);
  /**
   * Deserialize from bytes.
   */
  constructor(serialized: Uint8Array);
  constructor(arg: number | Uint8Array) {
    if (typeof arg === "number") {
      this.handle = arg;
    } else {
       this.handle = senderKeyMessageWasm.senderkeymessage_deserialize(arg);
    }
  }

  /**
   * @returns Distribution UUID
   */
  getDistributionId(): string {
    return senderKeyMessageWasm.senderkeymessage_get_distribution_id(this.handle);
  }

  /**
   * @returns Sender chain ID
   */
  getChainId(): number {
    return senderKeyMessageWasm.senderkeymessage_get_chain_id(this.handle);
  }

  /**
   * @returns Message iteration
   */
  getIteration(): number {
    return senderKeyMessageWasm.senderkeymessage_get_iteration(this.handle);
  }

  /**
   * @returns Ciphertext payload
   */
  getCiphertext(): Uint8Array {
    return senderKeyMessageWasm.senderkeymessage_get_ciphertext(this.handle);
  }

  /**
   * Verify the message signature.
   *
   * @throws InvalidMessageException if signature is invalid
   */
  verifySignature(signatureKey: ECPublicKey): void {

    const valid = senderKeyMessageWasm.senderkeymessage_verify_signature(
      this.handle,
      signatureKey.handle
    );

    if (!valid) {
      throw new InvalidMessageException("Invalid signature!");
    }
  }

  /**
   * Serialize this message.
   */
  serialize(): Uint8Array {
    return senderKeyMessageWasm.senderkeymessage_get_serialized(this.handle);
  }

  /**
   * Ciphertext message type.
   */
  getType(): number {
    return CiphertextMessageConstants.SENDERKEY_TYPE;
  }
}

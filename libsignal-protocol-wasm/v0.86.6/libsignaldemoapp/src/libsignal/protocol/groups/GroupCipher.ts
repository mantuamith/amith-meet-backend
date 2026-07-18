// Copyright 2014-2016 Signal Messenger, LLC.
// SPDX-License-Identifier: AGPL-3.0-only

import type { SignalProtocolAddress } from "../SignalProtocolAddress";
import {groupCipher as groupCipherWasm } from "libsignal_wasm_pqxdh";
import type { SenderKeyStore } from "./state/SenderKeyStore";
import type { CiphertextMessage } from "../message/CiphertextMessage";
import { CiphertextMessageFactory } from "../message/CiphertextMessageFactory";

/**
 * The main entry point for Signal Protocol group encrypt/decrypt operations.
 *
 * Once a session has been established with GroupSessionBuilder and a
 * SenderKeyDistributionMessage has been distributed to each member of the group,
 * this class can be used for all subsequent encrypt/decrypt operations within
 * that session.
 *
 * This class is NOT thread-safe.
 */
export class GroupCipher {
  private readonly senderKeyStore: SenderKeyStore;
  private readonly sender: SignalProtocolAddress;

  constructor(senderKeyStore: SenderKeyStore, sender: SignalProtocolAddress) {
    this.senderKeyStore = senderKeyStore;
    this.sender = sender;
  }

  /**
   * Encrypt a group message.
   *
   * @param distributionId UUID string identifying the group distribution
   * @param paddedPlaintext plaintext bytes (optionally padded)
   *
   * @throws NoSessionException
   */
  async encrypt(
    distributionId: string,
    paddedPlaintext: Uint8Array
  ): Promise<CiphertextMessage> {

    try {
      const msgPtr = await groupCipherWasm.groupcipher_encrypt_message(
        this.sender.handle,
        distributionId,
        paddedPlaintext,
        this.senderKeyStore.getStoreHandle()
      );

      return CiphertextMessageFactory.fromHandle(msgPtr);
    } catch (e) {
      // WASM already maps to correct Signal exceptions
      throw e;
    }
  }

  /**
   * Decrypt a SenderKey group message.
   *
   * @param senderKeyMessageBytes serialized SenderKeyMessage
   *
   * @throws LegacyMessageException
   * @throws DuplicateMessageException
   * @throws InvalidMessageException
   * @throws NoSessionException
   */
  async decrypt(senderKeyMessageBytes: Uint8Array): Promise<Uint8Array> {    
      return groupCipherWasm.groupcipher_decrypt_message(
        this.sender.handle,
        senderKeyMessageBytes,
        this.senderKeyStore.getStoreHandle()
      );    
  }
}

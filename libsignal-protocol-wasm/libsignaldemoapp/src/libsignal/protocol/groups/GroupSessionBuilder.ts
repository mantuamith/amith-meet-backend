import { SenderKeyDistributionMessage } from "../message/SenderKeyDistributionMessage";
import type { SignalProtocolAddress } from "../SignalProtocolAddress";
import type { SenderKeyStore } from "./state/SenderKeyStore";
import {groupSessionBuilder as groupSessionBuilderWasm} from "libsignal_wasm_pqxdh";


/**
 * Equivalent of:
 * org.signal.libsignal.protocol.groups.GroupSessionBuilder
 *
 * Responsible for setting up group SenderKey encrypted sessions.
 *
 * Sessions are unidirectional:
 * - sending OR receiving, never both
 */
export class GroupSessionBuilder {
  private readonly senderKeyStore: SenderKeyStore;

  constructor(senderKeyStore: SenderKeyStore) {
    this.senderKeyStore = senderKeyStore;
  }

  /**
   * Construct a group session for RECEIVING messages from a sender.
   *
   * @param sender The address of the device that sent the message
   * @param senderKeyDistributionMessage A received SenderKeyDistributionMessage
   */
  process(
    sender: SignalProtocolAddress,
    senderKeyDistributionMessage: SenderKeyDistributionMessage
  ): void {
      groupSessionBuilderWasm.groupsessionbuilder_process_sender_key_distribution_message(        
        sender.handle,
        senderKeyDistributionMessage.handle,
        this.senderKeyStore.getStoreHandle()
      );
  }

  /**
   * Construct a group session for SENDING messages.
   *
   * @param sender The address of the current client
   * @param distributionId An opaque UUID that uniquely identifies the group
   * @returns SenderKeyDistributionMessage to distribute to group members
   */
  async create(
    sender: SignalProtocolAddress,
    distributionId: string // UUID string
  ): Promise<SenderKeyDistributionMessage> {
    const ptr = await groupSessionBuilderWasm.groupsessionbuilder_create_sender_key_distribution_message(
        sender.handle,
        distributionId,
        this.senderKeyStore.getStoreHandle()
      );

    return new SenderKeyDistributionMessage(ptr);
  }
}

import type { SignalProtocolAddress } from "../../SignalProtocolAddress";
import type { SenderKeyStore } from "./SenderKeyStore";

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
    using senderGuard = new NativeHandleGuard(sender);
    using skdmGuard = new NativeHandleGuard(senderKeyDistributionMessage);

    filterExceptions(() =>
      Native.groupsessionbuilder_process_sender_key_distribution_message(
        senderGuard.nativeHandle,
        skdmGuard.nativeHandle,
        this.senderKeyStore
      )
    );
  }

  /**
   * Construct a group session for SENDING messages.
   *
   * @param sender The address of the current client
   * @param distributionId An opaque UUID that uniquely identifies the group
   * @returns SenderKeyDistributionMessage to distribute to group members
   */
  create(
    sender: SignalProtocolAddress,
    distributionId: string // UUID string
  ): SenderKeyDistributionMessage {
    using senderGuard = new NativeHandleGuard(sender);

    const ptr = filterExceptions(() =>
      Native.groupsessionbuilder_create_sender_key_distribution_message(
        senderGuard.nativeHandle,
        distributionId,
        this.senderKeyStore
      )
    );

    return new SenderKeyDistributionMessage(ptr);
  }
}

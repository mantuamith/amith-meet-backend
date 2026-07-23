
export interface GroupSessionBuilderWasm {
  groupsessionbuilder_process_sender_key_distribution_message(
    senderPtr: number,
    skdmPtr: number,
    senderKeyStoreHandle: number
  ): void;

  groupsessionbuilder_create_sender_key_distribution_message(
    senderPtr: number,
    distributionId: string,
    senderKeyStoreHandle: number
  ): number;
}

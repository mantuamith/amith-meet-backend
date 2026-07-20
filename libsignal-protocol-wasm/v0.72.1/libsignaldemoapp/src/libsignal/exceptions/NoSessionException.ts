import type { SignalProtocolAddress } from "../protocol/SignalProtocolAddress";

export class NoSessionException extends Error {
  public readonly address?: SignalProtocolAddress;

  constructor(message: string);
  constructor(address: SignalProtocolAddress | null, message: string);
  constructor(addressOrMessage: any, maybeMessage?: string) {

    // Case 1: new NoSessionException("message")
    if (typeof addressOrMessage === "string") {
      super(addressOrMessage);
      this.address = undefined;
      this.name = "NoSessionException";
      return;
    }

    // Case 2: new NoSessionException(address, "message")
    const address = addressOrMessage as SignalProtocolAddress | null;
    const message = maybeMessage ?? "No session";

    super(message);
    this.address = address ?? undefined;
    this.name = "NoSessionException";
  }
}
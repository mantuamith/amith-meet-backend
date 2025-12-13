import type { IdentityKey } from "../../protocol/IdentityKey";
import type { Direction } from "../Direction";
import type { IdentityChange } from "../IdentityChange";
import type { IdentityKeyStore } from "../IdentityKeyStore";
import type { SignalProtocolAddress } from "../../protocol/SignalProtocolAddress";
import type { IdentityKeyPair } from "../../protocol/IdentityKeyPair";

export class InMemoryIdentityKeyStore implements IdentityKeyStore {

  private trustedKeys = new Map<SignalProtocolAddress, IdentityKey>();

  private readonly identityKeyPair: IdentityKeyPair;
  private readonly localRegistrationId: number;

  constructor(identityKeyPair: IdentityKeyPair, localRegistrationId: number) {
    this.identityKeyPair = identityKeyPair;
    this.localRegistrationId = localRegistrationId;
  }

  // ---------------------------------------------------------------------------
  // IdentityKeyStore API
  // ---------------------------------------------------------------------------
  /*
  async getIdentityKeyPair(): Promise<IdentityKeyPair> {
    return this.identityKeyPair;
  } */

  async getIdentityKeyPair(): Promise<Uint8Array> {
    return this.identityKeyPair.serialize();
  }

  async getLocalRegistrationId(): Promise<number> {
    return this.localRegistrationId;
  }

async saveIdentity(
  address: SignalProtocolAddress,
  identityKey: IdentityKey
): Promise<IdentityChange> {
  const existing = this.trustedKeys.get(address);

  this.trustedKeys.set(address, identityKey);

  if (!existing || identityKey.equals(existing)) {
    return "NEW_OR_UNCHANGED";
  } else {
    return "REPLACED_EXISTING";
  }
}

  async isTrustedIdentity(
    address: SignalProtocolAddress,
    identityKey: IdentityKey,
    direction: Direction
  ): Promise<boolean> {
    const trusted = this.trustedKeys.get(address);
    return trusted === undefined || trusted.equals(identityKey);
  }

  getIdentity(address: SignalProtocolAddress): IdentityKey | null {
    return this.trustedKeys.get(address) ?? null;
  }
}
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

  getIdentityKeyPair(): IdentityKeyPair {
    return this.identityKeyPair;
  }

  getLocalRegistrationId(): number {
    return this.localRegistrationId;
  }

saveIdentity(
  address: SignalProtocolAddress,
  identityKey: IdentityKey
): IdentityChange {
  const existing = this.trustedKeys.get(address);

  this.trustedKeys.set(address, identityKey);

  if (!existing || identityKey.equals(existing)) {
    return "NEW_OR_UNCHANGED";
  } else {
    return "REPLACED_EXISTING";
  }
}

  isTrustedIdentity(
    address: SignalProtocolAddress,
    identityKey: IdentityKey,
    direction: Direction
  ): boolean {
    const trusted = this.trustedKeys.get(address);
    return trusted === undefined || trusted.equals(identityKey);
  }

  getIdentity(address: SignalProtocolAddress): IdentityKey | null {
    return this.trustedKeys.get(address) ?? null;
  }
}
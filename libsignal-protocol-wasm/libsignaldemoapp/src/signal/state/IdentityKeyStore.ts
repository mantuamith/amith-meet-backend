import type { IdentityKey } from "../protocol/IdentityKey";
import type { Direction } from "./Direction";
import type { IdentityChange } from "./IdentityChange";
import type { IdentityKeyPair } from "./IdentityKeyPair";
import type { SignalProtocolAddress } from "../protocol/SignalProtocolAddress";

/**
 * TypeScript equivalent of Signal's IdentityKeyStore interface.
 *
 * Describes persistent storage of:
 * - local identity key pair
 * - remote identity keys (trusted or not)
 */
export interface IdentityKeyStore {

  /**
   * Get the local client's persistent identity key pair.
   */
  getIdentityKeyPair(): IdentityKeyPair;

  /**
   * Return the local client's registration ID.
   * A random 1–16380 value, generated at install time.
   */
  getLocalRegistrationId(): number;

  /**
   * Save a remote client's identity key as trusted.
   *
   * Returns:
   *  - "REPLACED_EXISTING" if a different identity existed before
   *  - "NEW_OR_UNCHANGED" otherwise
   */
  saveIdentity(
    address: SignalProtocolAddress,
    identityKey: IdentityKey
  ): IdentityChange;

  /**
   * Determine whether a remote identity key is trusted.
   *
   * Trust-on-first-use (TOFU) semantics:
   *  - if no previous identity exists → trusted
   *  - if identity matches stored identity → trusted
   *  - otherwise → untrusted
   */
  isTrustedIdentity(
    address: SignalProtocolAddress,
    identityKey: IdentityKey,
    direction: Direction
  ): boolean;

  /**
   * Return the trusted identity for a remote client.
   * Returns null if no identity is saved.
   */
  getIdentity(
    address: SignalProtocolAddress
  ): IdentityKey | null;
}
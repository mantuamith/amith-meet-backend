import type { ECPublicKey } from "../../protocol/ecc/ECPublicKey";
import type { IdentityKey } from "../../protocol/IdentityKey";
import type { Direction } from "../Direction";
import type { KyberPreKeyRecord } from "../KyberPreKeyRecord";
import type { PreKeyRecord } from "../PreKeyRecord";
import type { SessionRecord } from "../SessionRecord";
import type { SignalProtocolAddress } from "../../protocol/SignalProtocolAddress";
import type { SignalProtocolStore } from "../SignalProtocolStore";
import type { SignedPreKeyRecord } from "../SignedPreKeyRecord";
import type { UUID } from "../UUID";
import { InMemoryIdentityKeyStore } from "./InMemoryIdentityKeyStore";
import { InMemoryKyberPreKeyStore } from "./InMemoryKyberPreKeyStore";
import { InMemoryPreKeyStore } from "./InMemoryPreKeyStore";
import { InMemorySessionStore } from "./InMemorySessionStore.ts";
import { InMemorySignedPreKeyStore } from "./InMemorySignedPreKeyStore";
import type { IdentityKeyPair } from "../../protocol/IdentityKeyPair";
import { InMemorySenderKeyStore } from "../../protocol/groups/state/InMemorySenderKeyStore.ts";
import type { SenderKeyRecord } from "../../protocol/groups/state/SenderKeyRecord.ts";

/**
 * Full TypeScript equivalent of Signal's InMemorySignalProtocolStore.
 * Composes all sub-stores: identity, session, prekey, signed prekey,
 * kyber (post-quantum), and sender-key (group messaging).
 */
export class InMemorySignalProtocolStore implements SignalProtocolStore {

  private readonly preKeyStore = new InMemoryPreKeyStore();
  private readonly sessionStore = new InMemorySessionStore();
  private readonly signedPreKeyStore = new InMemorySignedPreKeyStore();
  private readonly kyberPreKeyStore = new InMemoryKyberPreKeyStore();
  private readonly senderKeyStore = new InMemorySenderKeyStore();

  private readonly identityKeyStore: InMemoryIdentityKeyStore;

  constructor(identityKeyPair: IdentityKeyPair, registrationId: number) {
    this.identityKeyStore = new InMemoryIdentityKeyStore(identityKeyPair, registrationId);
    
  }
 
  // --------------------------------------------------------------------
  // Identity store
  // --------------------------------------------------------------------

  getIdentityKeyPair(): Promise<Uint8Array> {
    return this.identityKeyStore.getIdentityKeyPair();
  }

  getLocalRegistrationId(): Promise<number> {
    return this.identityKeyStore.getLocalRegistrationId();
  }

  saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey) {
    return this.identityKeyStore.saveIdentity(address, identityKey);
  }

  isTrustedIdentity(
    address: SignalProtocolAddress,
    identityKey: IdentityKey,
    direction: Direction
  ): Promise<boolean> {
    return this.identityKeyStore.isTrustedIdentity(address, identityKey, direction);
  }

  getIdentity(address: SignalProtocolAddress): IdentityKey | null {
    return this.identityKeyStore.getIdentity(address);
  }

  getIdentityKeyStoreHandle(): number {
    return this.identityKeyStore.getIdentityKeyStoreHandle();
  }

  // --------------------------------------------------------------------
  // PreKey store
  // --------------------------------------------------------------------

  loadPreKey(preKeyId: number): Promise<PreKeyRecord> {
    return this.preKeyStore.loadPreKey(preKeyId);
  }

  storePreKey(preKeyId: number, record: PreKeyRecord): Promise<void> {
    return this.preKeyStore.storePreKey(preKeyId, record);
  }

  containsPreKey(preKeyId: number): boolean {
    return this.preKeyStore.containsPreKey(preKeyId);
  }

  removePreKey(preKeyId: number): Promise<void> {
    return this.preKeyStore.removePreKey(preKeyId);
  }

  getPreKeyStoreHandle(): number {
    return this.preKeyStore.getPreKeyStoreHandle();
  }

  // --------------------------------------------------------------------
  // Session store
  // --------------------------------------------------------------------

  loadSession(address: SignalProtocolAddress): Promise<SessionRecord | null>  {
    return this.sessionStore.loadSession(address);
  }

  loadExistingSessions(
    addresses: SignalProtocolAddress[]
  ): SessionRecord[] {
    return this.sessionStore.loadExistingSessions(addresses);
  }

  storeSession(address: SignalProtocolAddress, serialized: Uint8Array): Promise<void> {
    return this.sessionStore.storeSession(address, serialized);
  }

  containsSession(address: SignalProtocolAddress): boolean {
    return this.sessionStore.containsSession(address);
  }

  deleteSession(address: SignalProtocolAddress): void {
    this.sessionStore.deleteSession(address);
  }

  getSessionStoreHandle(): number {
    return this.sessionStore.getSessionStoreHandle();
  }

  // --------------------------------------------------------------------
  // Signed pre-key store
  // --------------------------------------------------------------------

  loadSignedPreKey(id: number): Promise<SignedPreKeyRecord> {
    return this.signedPreKeyStore.loadSignedPreKey(id);
  }

  loadSignedPreKeys(): SignedPreKeyRecord[] {
    return this.signedPreKeyStore.loadSignedPreKeys();
  }

  storeSignedPreKey(id: number, record: SignedPreKeyRecord): Promise<void> {
    return this.signedPreKeyStore.storeSignedPreKey(id, record);
  }

  containsSignedPreKey(id: number): boolean {
    return this.signedPreKeyStore.containsSignedPreKey(id);
  }

  removeSignedPreKey(id: number): void {
    this.signedPreKeyStore.removeSignedPreKey(id);
  }

  getSignedPreKeyStoreHandle(): number {
    return this.signedPreKeyStore.getSignedPreKeyStoreHandle();
  }

  // --------------------------------------------------------------------
  // Sender-key (groups) store
  // --------------------------------------------------------------------

  storeSenderKey(
    sender: SignalProtocolAddress,
    distributionId: UUID,
    record: SenderKeyRecord
  ): void {
    this.senderKeyStore.storeSenderKey(sender, distributionId, record);
  }

  loadSenderKey(
    sender: SignalProtocolAddress,
    distributionId: UUID
  ): SenderKeyRecord | null {
    return this.senderKeyStore.loadSenderKey(sender, distributionId);
  }

   getStoreHandle(): number {
    throw this.senderKeyStore.getStoreHandle();
  }

  // --------------------------------------------------------------------
  // Kyber Post-Quantum PreKey Store
  // --------------------------------------------------------------------

  loadKyberPreKey(id: number): KyberPreKeyRecord {
    return this.kyberPreKeyStore.loadKyberPreKey(id);
  }

  loadKyberPreKeys(): KyberPreKeyRecord[] {
    return this.kyberPreKeyStore.loadKyberPreKeys();
  }

  storeKyberPreKey(id: number, record: KyberPreKeyRecord): void {
    this.kyberPreKeyStore.storeKyberPreKey(id, record);
  }

  containsKyberPreKey(id: number): boolean {
    return this.kyberPreKeyStore.containsKyberPreKey(id);
  }

  markKyberPreKeyUsed(
    kyberPreKeyId: number,
    signedPreKeyId: number,
    baseKey: ECPublicKey
  ): void {
    this.kyberPreKeyStore.markKyberPreKeyUsed(kyberPreKeyId, signedPreKeyId, baseKey);
  }

  hasKyberPreKeyBeenUsed(id: number): boolean {
    return this.kyberPreKeyStore.hasKyberPreKeyBeenUsed(id);
  }

  getKyberPreKeyStoreHandle(): number {
    return this.kyberPreKeyStore.getKyberPreKeyStoreHandle();
  }
}
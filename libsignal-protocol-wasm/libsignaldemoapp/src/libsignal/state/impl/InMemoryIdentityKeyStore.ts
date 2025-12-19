import { IdentityKey } from "../../protocol/IdentityKey";
import { directionToNumber, type Direction } from "../Direction";
import type { IdentityChange } from "../IdentityChange";
import type { IdentityKeyStore } from "../IdentityKeyStore";
import type { SignalProtocolAddress } from "../../protocol/SignalProtocolAddress";
import type { IdentityKeyPair } from "../../protocol/IdentityKeyPair";
import { identityKeyStore as  identityKeyStoreWasm } from "../../../../../libsignal-protocol-wasm/wasm-wrapper/pkg/libsignal_wasm_pqxdh";

export class InMemoryIdentityKeyStore implements IdentityKeyStore {
  private readonly storeHandle!: number;

  constructor(identityKeyPair: IdentityKeyPair, localRegistrationId: number) {      
    this.storeHandle = identityKeyStoreWasm.identitykeystore_create_identity_key_store(
      identityKeyPair.publicKey.publicKey.handle,
      identityKeyPair.privateKey.handle,
      localRegistrationId);
  }

  async getIdentityKeyPair(): Promise<Uint8Array> {
    return identityKeyStoreWasm.identitykeystore_get_identity_key_pair(this.storeHandle);
  }

  async getLocalRegistrationId(): Promise<number> {
    return identityKeyStoreWasm.identitykeystore_get_local_registration_id(this.storeHandle);
  }

async saveIdentity(
    address: SignalProtocolAddress,
    identityKey: IdentityKey
  ): Promise<IdentityChange> {
  identityKeyStoreWasm.identitykeystore_save_identity(this.storeHandle, address.handle, identityKey.serialize());
  return "NEW_OR_UNCHANGED";
}

  async isTrustedIdentity(
    address: SignalProtocolAddress,
    identityKey: IdentityKey,
    direction: Direction
  ): Promise<boolean> {
    return identityKeyStoreWasm.identitykeystore_is_trusted_identity(this.storeHandle, address.handle, identityKey.serialize(),
     directionToNumber(direction));
  }

  getIdentity(address: SignalProtocolAddress): IdentityKey | null {
    const bytes =
    identityKeyStoreWasm.identitykeystore_get_identity(
      this.storeHandle,
      address.handle
    );

  if (!bytes) {
    return null; // TOFU / no identity stored
  }

  return new IdentityKey(bytes);
  }

  getIdentityKeyStoreHandle(): number {
    return this.storeHandle;
  }
}
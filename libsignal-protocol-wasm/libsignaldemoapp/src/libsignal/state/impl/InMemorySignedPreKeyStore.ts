import { InvalidKeyIdException } from "../../exceptions/InvalidKeyIdException";
import { SignedPreKeyRecord } from "../SignedPreKeyRecord";
import type { SignedPreKeyStore } from "../SignedPreKeyStore";
import { signedPreKeyStore as signedPreKeyStoreWasm } from "../../../../../libsignal-protocol-wasm/wasm-wrapper/pkg/libsignal_wasm_pqxdh";

export class InMemorySignedPreKeyStore implements SignedPreKeyStore {
  private readonly storeHandle!: number;

  constructor() {
    this.storeHandle = signedPreKeyStoreWasm.signedprekeystore_create();
  }
  getSignedPreKeyStoreHandle(): number {
    return this.storeHandle;
  }

  async loadSignedPreKey(id: number): Promise<SignedPreKeyRecord> {
    const serialized = signedPreKeyStoreWasm.signedprekeystore_load_signed_prekey(this.storeHandle, id);

    if (!serialized) {
      throw new InvalidKeyIdException(`No such SignedPreKeyRecord! ${id}`);
    }

    try {
      return new SignedPreKeyRecord(serialized);
    } catch (e) {
      throw new Error(`AssertionError: ${(e as Error).message}`);
    }
  }

  loadSignedPreKeys(): SignedPreKeyRecord[] {
    const results: SignedPreKeyRecord[] = [];

    try {
      for (const serialized of signedPreKeyStoreWasm.signedprekeystore_load_signed_prekeys(this.storeHandle)) {
        results.push(new SignedPreKeyRecord(serialized));
      }
    } catch (e) {
      throw new Error(`AssertionError: ${(e as Error).message}`);
    }

    return results;
  }

  async storeSignedPreKey(id: number, record: SignedPreKeyRecord): Promise<void> {
    signedPreKeyStoreWasm.signedprekeystore_store_signed_prekey(this.storeHandle, id, record.serialize());
  }

  containsSignedPreKey(id: number): boolean {
    return signedPreKeyStoreWasm.signedprekeystore_contains_signed_prekey(this.storeHandle, id);
  }

  removeSignedPreKey(id: number): void {
    signedPreKeyStoreWasm.signedprekeystore_remove_signed_prekey(this.storeHandle, id);
  }
}
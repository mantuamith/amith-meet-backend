import { PreKeyRecord } from "../PreKeyRecord";
import type { PreKeyStore } from "../PreKeyStore";
import { preKeyStore as preKeyStoreWasm } from "../../../../../libsignal-protocol-wasm/wasm-wrapper/pkg/libsignal_wasm_pqxdh";

export class InMemoryPreKeyStore implements PreKeyStore {
   private readonly storeHandle!: number;
   
  constructor() {
    this.storeHandle = preKeyStoreWasm.prekeystore_create();
  }

  async loadPreKey(preKeyId: number): Promise<PreKeyRecord> {
    try {
      const raw = preKeyStoreWasm.prekeystore_load_prekey(this.storeHandle, preKeyId);
      return new PreKeyRecord(raw);
    } catch (e) {
      throw new Error(`AssertionError: ${(e as Error).message}`);
    }
  }

  async storePreKey(preKeyId: number, record: PreKeyRecord): Promise<void> {
    preKeyStoreWasm.prekeystore_store_prekey(this.storeHandle, preKeyId, record.serialize());
  }

  containsPreKey(preKeyId: number): boolean {
    return preKeyStoreWasm.prekeystore_contains_prekey(this.storeHandle, preKeyId);
  }

  async removePreKey(preKeyId: number): Promise<void> {
    preKeyStoreWasm.prekeystore_remove_prekey(this.storeHandle, preKeyId);
  }

  getPreKeyStoreHandle(): number {
    return this.storeHandle;
  }  
}
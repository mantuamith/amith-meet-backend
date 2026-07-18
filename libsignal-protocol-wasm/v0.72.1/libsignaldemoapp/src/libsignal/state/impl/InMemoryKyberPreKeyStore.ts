import { InvalidKeyIdException } from "../../exceptions/InvalidKeyIdException";
import type { ECPublicKey } from "../../protocol/ecc/ECPublicKey";
import { KyberPreKeyRecord } from "../KyberPreKeyRecord";
import type { KyberPreKeyStore } from "../KyberPreKeyStore";
import { kyberPreKeyStore as kyberPreKeyStoreWasm } from "libsignal_wasm_pqxdh";

export class InMemoryKyberPreKeyStore implements KyberPreKeyStore {
  private readonly storeHandle!: number;

  constructor() {
    this.storeHandle = kyberPreKeyStoreWasm.kyberprekeystore_create();
  }

  // ---------------------------------------------------------------------------
  // loadKyberPreKey()
  // ---------------------------------------------------------------------------

  loadKyberPreKey(id: number): KyberPreKeyRecord {
    const serialized = kyberPreKeyStoreWasm.kyberprekeystore_load_kyber_prekey(this.storeHandle, id);

    if (!serialized) {
      throw new InvalidKeyIdException(`No such KyberPreKeyRecord! ${id}`);
    }

    try {
      return new KyberPreKeyRecord(serialized);
    } catch (e) {
      throw new Error(`AssertionError: ${(e as Error).message}`);
    }
  }

  // ---------------------------------------------------------------------------
  // loadKyberPreKeys()
  // ---------------------------------------------------------------------------

  loadKyberPreKeys(): KyberPreKeyRecord[] {
    const results: KyberPreKeyRecord[] = [];

    for (const serialized of kyberPreKeyStoreWasm.kyberprekeystore_load_kyber_prekeys(this.storeHandle)) {
      try {
        results.push(new KyberPreKeyRecord(serialized));
      } catch (e) {
        throw new Error(`AssertionError: ${(e as Error).message}`);
      }
    }

    return results;
  }

  // ---------------------------------------------------------------------------
  // storeKyberPreKey()
  // ---------------------------------------------------------------------------

  storeKyberPreKey(id: number, record: KyberPreKeyRecord): void {
    kyberPreKeyStoreWasm.kyberprekeystore_store_kyber_prekey(this.storeHandle, id, record.serialize());
  }

  // ---------------------------------------------------------------------------
  // containsKyberPreKey()
  // ---------------------------------------------------------------------------

  containsKyberPreKey(id: number): boolean {
    return kyberPreKeyStoreWasm.kyberprekeystore_contains_kyber_prekey(this.storeHandle, id);
  }

  // ---------------------------------------------------------------------------
  // markKyberPreKeyUsed()
  // ---------------------------------------------------------------------------

  markKyberPreKeyUsed(
    kyberPreKeyId: number
  ): void {
    kyberPreKeyStoreWasm.kyberprekeystore_mark_kyber_prekey_used(this.storeHandle, kyberPreKeyId);    
  }

  // ---------------------------------------------------------------------------
  // hasKyberPreKeyBeenUsed()
  // ---------------------------------------------------------------------------

  hasKyberPreKeyBeenUsed(id: number): boolean {
    return kyberPreKeyStoreWasm.kyberprekeystore_has_kyber_prekey_been_used(this.storeHandle, id);
  }

  getKyberPreKeyStoreHandle(): number {
    return this.storeHandle;
  }
}
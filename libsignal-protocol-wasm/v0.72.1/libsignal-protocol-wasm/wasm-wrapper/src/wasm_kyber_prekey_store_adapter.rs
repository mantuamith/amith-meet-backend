use async_trait::async_trait;
use js_sys::Uint8Array;
use web_sys::console;

use libsignal_protocol::{
    KyberPreKeyStore,
    KyberPreKeyId,
    KyberPreKeyRecord,
    PublicKey,
    SignalProtocolError,
    SignedPreKeyId,
};

use libsignal_protocol::error::Result as ProtocolResult;
use libsignal_protocol::GenericSignedPreKey;

use crate::wasm_kyber_prekey_store;
use crate::wasm_ec_public_key;

/// Helper: map JsValue → SignalProtocolError
fn js_err(e: impl core::fmt::Debug) -> SignalProtocolError {
    SignalProtocolError::InvalidArgument(format!("{:?}", e))
}

//
// =====================
// KyberPreKeyStore Adapter
// =====================
//
pub struct KyberPreKeyStoreAdapter {
    handle: u32,
}

impl KyberPreKeyStoreAdapter {
    pub fn new(handle: u32) -> Self {
        Self { handle }
    }
}

#[async_trait(?Send)]
impl KyberPreKeyStore for KyberPreKeyStoreAdapter {
    async fn get_kyber_pre_key(
        &self,
        id: KyberPreKeyId,
    ) -> ProtocolResult<KyberPreKeyRecord> {
        let bytes =
            wasm_kyber_prekey_store::kyberprekeystore_load_kyber_prekey(
                self.handle,
                id.into(),
            )
            .map_err(js_err)?;

        KyberPreKeyRecord::deserialize(&bytes.to_vec())
    }

    async fn save_kyber_pre_key(
        &mut self,
        id: KyberPreKeyId,
        record: &KyberPreKeyRecord,
    ) -> ProtocolResult<()> {
        wasm_kyber_prekey_store::kyberprekeystore_store_kyber_prekey(
            self.handle,
            id.into(),
            Uint8Array::from(record.serialize()?.as_slice()),
        )
        .map_err(js_err)
    }

    async fn mark_kyber_pre_key_used(
        &mut self,
        kyber_prekey_id: KyberPreKeyId,
        signed_prekey_id: SignedPreKeyId,
        base_key: &PublicKey,
    ) -> ProtocolResult<()> {
        let base_key_handle =
            wasm_ec_public_key::store_public_key(base_key.clone());

        wasm_kyber_prekey_store::kyberprekeystore_mark_kyber_prekey_used(
            self.handle,
            kyber_prekey_id.into(),
            signed_prekey_id.into(),
            base_key_handle,
        )
        .map_err(js_err)
    }
}

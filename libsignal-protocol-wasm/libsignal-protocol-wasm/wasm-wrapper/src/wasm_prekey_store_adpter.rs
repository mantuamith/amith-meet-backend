use async_trait::async_trait;
use js_sys::Uint8Array;
use web_sys::console;

use libsignal_protocol::{
    PreKeyStore,
    IdentityKey,
    IdentityKeyPair,
    IdentityChange,
    Direction,
    ProtocolAddress,
    PreKeyId,
    PreKeyRecord,
    PublicKey,
    SignalProtocolError,
};

use libsignal_protocol::error::Result as ProtocolResult;
use libsignal_protocol::GenericSignedPreKey;

use crate::wasm_session_store::{
    store_session as wasm_store_session,
    load_session as wasm_load_session,
};

use crate::wasm_identity_key_store::{
    store_identity_key as wasm_store_identity_key,
    load_identity_key as wasm_load_identity_key,
    get_identity_key_pair as wasm_get_identity_key_pair,
    get_local_registration_id as wasm_get_local_registration_id,
    is_trusted_identity as wasm_is_trusted_identity,
};

use crate::wasm_prekey_store;
use crate::wasm_signed_prekey_store;
use crate::wasm_kyber_prekey_store;
use crate::wasm_ec_public_key;

/// Helper: map JsValue → SignalProtocolError
fn js_err(e: impl core::fmt::Debug) -> SignalProtocolError {
    SignalProtocolError::InvalidArgument(format!("{:?}", e))
}


//
// =====================
// PreKeyStore Adapter
// =====================
//
pub struct PreKeyStoreAdapter {
    handle: u32,
}

impl PreKeyStoreAdapter {
    pub fn new(handle: u32) -> Self {
        Self { handle }
    }
}

#[async_trait(?Send)]
impl PreKeyStore for PreKeyStoreAdapter {
    async fn get_pre_key(
        &self,
        id: PreKeyId,
    ) -> ProtocolResult<PreKeyRecord> {
        let bytes =
            wasm_prekey_store::prekeystore_load_prekey(
                self.handle,
                id.into(),
            )
            .map_err(js_err)?;

        PreKeyRecord::deserialize(&bytes.to_vec())
    }

    async fn save_pre_key(
        &mut self,
        id: PreKeyId,
        record: &PreKeyRecord,
    ) -> ProtocolResult<()> {
        wasm_prekey_store::prekeystore_store_prekey(
            self.handle,
            id.into(),
            Uint8Array::from(record.serialize()?.as_slice()),
        )
        .map_err(js_err)
    }

    async fn remove_pre_key(
        &mut self,
        id: PreKeyId,
    ) -> ProtocolResult<()> {
        wasm_prekey_store::prekeystore_remove_prekey(
            self.handle,
            id.into(),
        )
        .map_err(js_err)
    }
}


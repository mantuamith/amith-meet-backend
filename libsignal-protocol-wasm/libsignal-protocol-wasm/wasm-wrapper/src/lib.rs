// ------------------------
// IMPORTS
// ------------------------
use wasm_bindgen::prelude::*;
use serde::{Deserialize, Serialize};
use base64::{engine::general_purpose, Engine as _};

use hkdf::Hkdf;
use sha2::Sha256;

use js_sys::{Object, Reflect, Uint8Array};

use web_sys::console;


use base64::engine::general_purpose::STANDARD as B64;
use serde_wasm_bindgen;
use serde_json::json;

// Import libsignal-protocol bindings
use libsignal_protocol::identity_key::wasm_identity::{
    x25519_dh_wasm,
    x25519_pub_from_priv_wasm,
};
use libsignal_protocol::kem::wasm_helpers::{
    kyber_encapsulate_wasm_from_bytes,
    kyber_decapsulate_wasm_from_bytes,
};

mod protocol;
mod wasm_ec_public_key;
mod wasm_ec_private_key;
mod wasm_protocol_address;
mod wasm_identity_key_pair;
mod wasm_kem_secret_key;
mod wasm_kem_public_key;
mod wasm_kem_key_pair;
mod wasm_prekey_bundle;
mod wasm_session_builder;
mod wasm_session_record;
mod wasm_prekey_signal_message;
mod handle_table;
mod wasm_signal_message;
mod utils;
mod wasm_session_cipher;
mod wasm_ciphertext_message;
mod handle_store;
mod handle_identity_store;
mod wasm_session_store;
mod wasm_identity_key_store;
mod wasm_prekey_record;
mod wasm_signed_prekey_record;
mod wasm_kyber_prekey_record;
mod wasm_prekey_store;
mod wasm_signed_prekey_store;
mod wasm_kyber_prekey_store;

// Re-export each module's wasm_bindgen API
pub use protocol::*;
pub use wasm_ec_public_key::*;
pub use wasm_ec_private_key::*;
pub use wasm_protocol_address::*;
pub use wasm_identity_key_pair::*;
pub use wasm_kem_secret_key::*;
pub use wasm_kem_public_key::*;
pub use wasm_kem_key_pair::*;
pub use wasm_prekey_bundle::*;
pub use wasm_session_record::*;
pub use wasm_session_builder::*;
pub use wasm_prekey_signal_message::*;
pub use handle_table::*;
pub use wasm_signal_message::*;
pub use utils::*;
pub use wasm_session_cipher::*;
pub use wasm_ciphertext_message::*;
pub use handle_store::*;
pub use wasm_session_store::*;
pub use wasm_identity_key_store::*;
pub use wasm_prekey_record::*;
pub use wasm_signed_prekey_record::*;
pub use wasm_kyber_prekey_record::*;
pub use wasm_prekey_store::*;
pub use wasm_signed_prekey_store::*;
pub use wasm_kyber_prekey_store::*;

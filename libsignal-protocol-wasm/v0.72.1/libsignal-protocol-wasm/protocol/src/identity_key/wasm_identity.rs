//! Small wasm-friendly wrappers for identity / DH operations.
//! Returns plain Vec<u8> so wasm clients (and your wasm-wrapper crate) can call easily.
use web_sys::console;
use crate::{PrivateKey, PublicKey, Result, SignalProtocolError};

/// Perform X25519 DH: priv_bytes (private key) × pub_bytes (public key).
/// Returns shared secret as Vec<u8>.
pub fn x25519_dh_wasm(priv_bytes: &[u8], pub_bytes: &[u8]) -> Result<Vec<u8>> {
    // deserialize private key
    let privkey = PrivateKey::deserialize(priv_bytes)
        .map_err(|_| SignalProtocolError::InvalidArgument("Invalid private key".into()))?;

    // deserialize public key
    let pubkey = PublicKey::try_from(pub_bytes)
        .map_err(|_| SignalProtocolError::InvalidArgument("Invalid public key".into()))?;

    // use the core API available in your fork: calculate_agreement(&self, &PublicKey)
    let shared_box = privkey
        .calculate_agreement(&pubkey)
        .map_err(|_| SignalProtocolError::InvalidArgument("DH failed".into()))?;

    // calculate_agreement returns Box<[u8]>, convert to Vec<u8>
    Ok(shared_box.to_vec())
}

/// Derive public key bytes from a private key (X25519 pub-from-priv).
pub fn x25519_pub_from_priv_wasm(priv_bytes: &[u8]) -> Result<Vec<u8>> {

    // console::log_1(&format!("WASM: priv_bytes (base64) = {} {}", base64::encode(priv_bytes), priv_bytes.len()).into());
    let privkey =
        PrivateKey::deserialize(priv_bytes).map_err(|_| SignalProtocolError::InvalidArgument(
            " WASM Invalid private key".into(),
        ))?;
    
    let pubkey = privkey
        .public_key()
        .map_err(|_| SignalProtocolError::InvalidArgument("Cannot derive public key".into()))?;

    Ok(pubkey.serialize().to_vec())
}

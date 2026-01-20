use crate::kem::{KeyMaterial, Public, Secret, KeyType, SerializedCiphertext, SharedSecret};
use crate::{Result, SignalProtocolError};

use rand::CryptoRng;
use rand::SeedableRng;
use rand_chacha::ChaCha20Rng;
use getrandom;

//use crate::{Result, SignalProtocolError};
//use crate::kem::KeyType;
//use rand::SeedableRng;
//use rand_chacha::ChaCha20Rng;
//use getrandom;

#[allow(dead_code)]
/// Encapsulate using a `KeyMaterial<Public>`. Returns `(serialized_ciphertext, shared_secret)`.
pub fn kyber_encapsulate_wasm_from_keymaterial(
    pub_key: &KeyMaterial<Public>,
) -> Result<(SerializedCiphertext, SharedSecret)> {

    // Seed ChaCha20Rng using secure randomness
    let mut seed = [0u8; 32];
    getrandom::getrandom(&mut seed).map_err(|err| {
        SignalProtocolError::InvalidArgument(format!("Randomness error: {err}"))
    })?;

    let mut rng = ChaCha20Rng::from_seed(seed);

    // Perform Kyber encapsulation
    let (shared_secret, raw_ct) = KeyType::Kyber1024
        .parameters()
        .encapsulate(pub_key, &mut rng as &mut dyn CryptoRng)?;

    // Prepend KeyType byte
    let mut boxed = Vec::with_capacity(1 + raw_ct.len());
    boxed.push(KeyType::Kyber1024.value());
    boxed.extend_from_slice(&raw_ct);

    Ok((boxed.into_boxed_slice(), shared_secret))
}

#[allow(dead_code)]
/// Decapsulate using a `KeyMaterial<Secret>` and serialized ciphertext.
pub fn kyber_decapsulate_wasm_from_keymaterial(
    secret_key: &KeyMaterial<Secret>,
    serialized_ct: &[u8],
) -> Result<SharedSecret> {

    if serialized_ct.is_empty() {
        return Err(SignalProtocolError::NoKeyTypeIdentifier);
    }

    let key_type_byte = serialized_ct[0];
    if key_type_byte != KeyType::Kyber1024.value() {
        return Err(SignalProtocolError::WrongKEMKeyType(
            key_type_byte,
            KeyType::Kyber1024.value(),
        ));
    }

    let raw_ct = &serialized_ct[1..];
    KeyType::Kyber1024
        .parameters()
        .decapsulate(secret_key, raw_ct)
}

#[allow(dead_code)]
pub fn kyber_encapsulate_wasm_from_bytes(
    pub_key_bytes: &[u8],
) -> Result<(SerializedCiphertext, SharedSecret)> {
    let km = KeyMaterial::<Public>::new(pub_key_bytes.into());
    kyber_encapsulate_wasm_from_keymaterial(&km)
}

#[allow(dead_code)]
pub fn kyber_decapsulate_wasm_from_bytes(
    secret_key_bytes: &[u8],
    serialized_ct: &[u8],
) -> Result<SharedSecret> {
    let km = KeyMaterial::<Secret>::new(secret_key_bytes.into());
    kyber_decapsulate_wasm_from_keymaterial(&km, serialized_ct)
}

/// Generate a Kyber1024 keypair and return (pub_bytes, priv_bytes).
/// This MUST live inside the `protocol` crate because it uses crate-local types.
pub fn kyber_keygen_in_protocol() -> Result<(Vec<u8>, Vec<u8>)> {
    // secure seed for deterministic PRNG
    let mut seed = [0u8; 32];
    getrandom::getrandom(&mut seed).map_err(|e| {
        SignalProtocolError::InvalidArgument(format!("Randomness error: {e}"))
    })?;
    let mut rng = ChaCha20Rng::from_seed(seed);

    // call the internal parameters' generate method (allowed inside this crate)
    let (pk_km, sk_km) = KeyType::Kyber1024
        .parameters()
        .generate(&mut rng);

    // convert KeyMaterial<Public/Secret> -> raw bytes
    // KeyMaterial implements AsRef<[u8]>, so as_ref() returns &[u8]
    Ok((pk_km.as_ref().to_vec(), sk_km.as_ref().to_vec()))
}







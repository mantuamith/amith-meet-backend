//
// Copyright 2020 Signal Messenger, LLC.
// SPDX-License-Identifier: AGPL-3.0-only
//

#![allow(clippy::derive_partial_eq_without_eq)]

// Desktop / native: use generated OUT_DIR files.
#[cfg(not(target_arch = "wasm32"))]
include!(concat!(env!("OUT_DIR"), "/signal.proto.wire.rs"));

// WASM: include pre-generated file stored in repo.
#[cfg(target_arch = "wasm32")]
include!("generated/signal.proto.wire.rs");

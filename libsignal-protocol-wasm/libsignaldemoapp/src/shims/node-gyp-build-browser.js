// This forces @signalapp/libsignal-client to use the WASM loader
// src/shims/node-gyp-build-browser.js
// Pretend a "fake" native module exists but has no exports.
// Signal will accept this and skip actual Node loading.
export default function () {
  return {};
}
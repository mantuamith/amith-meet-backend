export type Direction = "SENDING" | "RECEIVING";

/**
 * Convert Direction to numeric value expected by WASM / libsignal.
 *
 * NOTE:
 * Rust ignores direction in IdentityKeyStore,
 * but WASM ABI still requires a number.
 */
export function directionToNumber(direction: Direction): number {
  switch (direction) {
    case "SENDING":
      return 0;
    case "RECEIVING":
      return 1;
    default:
      // Exhaustiveness guard (never happens, but keeps TS honest)
      const _exhaustive: never = direction;
      return _exhaustive;
  }
}

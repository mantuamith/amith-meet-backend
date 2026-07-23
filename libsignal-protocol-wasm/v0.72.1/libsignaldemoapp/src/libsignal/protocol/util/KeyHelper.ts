export class KeyHelper {
  private constructor() {}

  /**
   * Generate a registration ID. Clients should only do this once, at install time.
   *
   * @param extendedRange If true:
   *   → returns random int in range [1, 2^31 - 1]
   *   Otherwise:
   *   → returns random int in range [1, 16380]
   */
  static generateRegistrationId(extendedRange: boolean = false): number {
    const rand32 = KeyHelper.randomInt32();

    if (extendedRange) {
      // Java: nextInt(Integer.MAX_VALUE - 1) + 1
      const max = 0x7fffffff - 1; // Integer.MAX_VALUE - 1
      return (rand32 % max) + 1;
    }

    // Java: nextInt(16380) + 1
    return (rand32 % 16380) + 1;
  }

  /**
   * Returns a cryptographically secure unsigned 32-bit integer.
   * Works in browsers AND Node 19+ (where globalThis.crypto is available).
   */
  private static randomInt32(): number {
    if (!globalThis.crypto?.getRandomValues) {
      throw new Error("Crypto.getRandomValues not available in this environment");
    }

    const array = new Uint32Array(1);
    globalThis.crypto.getRandomValues(array);
    return array[0];
  }
}

export const KEMKeyType = {
  KYBER_1024: "KYBER_1024",
} as const;

export type KEMKeyType = typeof KEMKeyType[keyof typeof KEMKeyType];
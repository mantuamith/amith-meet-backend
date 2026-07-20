export class LegacyMessageException extends Error {
  constructor(msg: string) {
    super(msg);
    this.name = "LegacyMessageException";
  }
}
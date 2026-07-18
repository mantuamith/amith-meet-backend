export class UntrustedIdentityException extends Error {
  constructor(msg: string) {
    super(msg);
    this.name = "UntrustedIdentityException";
  }
}
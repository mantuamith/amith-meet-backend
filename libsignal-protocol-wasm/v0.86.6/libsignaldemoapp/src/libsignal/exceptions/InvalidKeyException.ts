export class InvalidKeyException extends Error {
  constructor(msg: string) {
    super(msg);
    this.name = "InvalidKeyException";
  }
}
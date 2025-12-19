export class InvalidKeyIdException extends Error {
  constructor(message: string) {
    super(message);
    this.name = "InvalidKeyIdException";
  }
}
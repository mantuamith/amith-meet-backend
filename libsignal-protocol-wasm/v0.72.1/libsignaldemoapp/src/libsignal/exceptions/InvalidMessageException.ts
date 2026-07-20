export class InvalidMessageException extends Error {
  constructor(message: string) {
    super(message);
    this.name = "InvalidMessageException";
  }
}
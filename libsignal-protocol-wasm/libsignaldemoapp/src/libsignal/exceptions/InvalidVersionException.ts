export class InvalidVersionException extends Error {
  constructor(msg: string) {
    super(msg);
    this.name = "InvalidVersionException";
  }
}
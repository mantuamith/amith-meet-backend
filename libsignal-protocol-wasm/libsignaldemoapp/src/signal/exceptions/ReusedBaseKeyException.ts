import { InvalidMessageException } from "./InvalidMessageException";

export class ReusedBaseKeyException extends InvalidMessageException {
  constructor();
  constructor(message: string);
  constructor(cause: unknown);
  constructor(message: string, cause: unknown);
  constructor(arg1?: any, arg2?: any) {
    // No-arg constructor
    if (arg1 === undefined) {
      super("Reused base key");
      return;
    }

    // new ReusedBaseKeyException(message)
    if (typeof arg1 === "string" && arg2 === undefined) {
      super(arg1);
      return;
    }

    // new ReusedBaseKeyException(cause)
    if (arg1 instanceof Error && arg2 === undefined) {
      super(arg1.message);
      this.cause = arg1;
      return;
    }

    // new ReusedBaseKeyException(message, cause)
    if (typeof arg1 === "string") {
      super(arg1);
      this.cause = arg2;
      return;
    }

    // Fallback (rare)
    super("Reused base key");
  }

  /** Optional cause, mirroring Java Throwable cause */
  cause?: unknown;
}
import type { SessionRecord } from "./SessionRecord";

export interface SessionStore {

  /**
   * Load a copy of the SessionRecord for the given remote HANDLE,
   * or return null if none exists.
   *
   * Implementations MUST return a deep copy so that modifications do not affect
   * stored state unless `storeSession()` is explicitly called.
   *
   * @param remoteHandle - Opaque numeric handle provided by WASM
   */
  loadSession(remoteHandle: number): Promise<SessionRecord | null>;

  /**
   * Load SessionRecords for multiple remote handles.
   *
   * @param remoteHandles - List of opaque numeric handles
   * @throws NoSessionException if any handle has no active session.
   */
  loadExistingSessions(remoteHandles: number[]): SessionRecord[];

  /**
   * Persist a SessionRecord for a given remote handle.
   *
   * @param remoteHandle - Opaque numeric handle
   * @param record - The SessionRecord to store
   */
  storeSession(remoteHandle: number,  record: Uint8Array): Promise<void>;

  /**
   * Check whether a session exists for the given remote handle.
   *
   * @param remoteHandle - Opaque numeric handle
   */
  containsSession(remoteHandle: number): boolean;

  /**
   * Remove a session for a given remote handle.
   *
   * @param remoteHandle - Opaque numeric handle
   */
  deleteSession(remoteHandle: number): void;

  /**
   * OPTIONAL: Remove all sessions for a given remote handle.
   * (Included for symmetry; WASM does not currently call this.)
   */
  deleteAllSessionsForHandle?(remoteHandle: number): void;
}

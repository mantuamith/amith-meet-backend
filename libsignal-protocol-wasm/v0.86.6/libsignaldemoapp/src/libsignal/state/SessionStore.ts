import type { SignalProtocolAddress } from "../protocol/SignalProtocolAddress";
import type { SessionRecord } from "./SessionRecord";

export interface SessionStore {

  /**
   * Load a copy of the SessionRecord for the given remote HANDLE,
   * or return null if none exists.
   *
   * Implementations MUST return a deep copy so that modifications do not affect
   * stored state unless `storeSession()` is explicitly called.
   *
   * @param address - User address
   */
  loadSession(address: SignalProtocolAddress): Promise<SessionRecord | null>;

  /**
   * Load SessionRecords for multiple remote handles.
   *
   * @param addresses - List of user address
   * @throws NoSessionException if any handle has no active session.
   */
  loadExistingSessions(addresses: SignalProtocolAddress[]): SessionRecord[];

  /**
   * Persist a SessionRecord for a given remote handle.
   *
   * @param address - User address
   * @param record - The SessionRecord to store
   */
  storeSession(address: SignalProtocolAddress,  record: Uint8Array): Promise<void>;

  /**
   * Check whether a session exists for the given remote handle.
   *
   * @param address - User address
   */
  containsSession(address: SignalProtocolAddress): boolean;

  /**
   * Remove a session for a given remote handle.
   *
   * @param address - User address
   */
  deleteSession(address: SignalProtocolAddress): void;

  /**
   * Return the store pointer to wasm
   */
  getSessionStoreHandle(): number;
}

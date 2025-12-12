import type { SessionRecord } from "./SessionRecord";
import type { SignalProtocolAddress } from "../protocol/SignalProtocolAddress";

export interface SessionStore {

  /**
   * Load a copy of the SessionRecord for the given address, or create a new one if none exists.
   *
   * Implementations MUST return a deep copy so that modifications do not affect
   * stored state unless `storeSession()` is explicitly called.
   *
   * @param address - The remote client's name + deviceId tuple.
   */
  loadSession(address: SignalProtocolAddress): SessionRecord;

  /**
   * Load SessionRecords for multiple addresses.
   *
   * @param addresses - List of remote client addresses.
   * @throws NoSessionException if any address has no active session.
   */
  loadExistingSessions(addresses: SignalProtocolAddress[]): SessionRecord[];

  /**
   * Return all device IDs that have active sessions for a given recipient.
   *
   * @param name - The recipient (username).
   */
  getSubDeviceSessions(name: string): number[];

  /**
   * Persist a SessionRecord for a given remote client.
   *
   * @param address - The remote client's address.
   * @param record - The SessionRecord to store.
   */
  storeSession(address: SignalProtocolAddress, record: SessionRecord): void;

  /**
   * Check whether a session exists for the given address.
   *
   * @param address - The remote client's address.
   */
  containsSession(address: SignalProtocolAddress): boolean;

  /**
   * Remove a session for a given address.
   *
   * @param address - The remote client's address.
   */
  deleteSession(address: SignalProtocolAddress): void;

  /**
   * Remove all sessions associated with a given recipient name.
   *
   * @param name - The recipient's name.
   */
  deleteAllSessions(name: string): void;
}
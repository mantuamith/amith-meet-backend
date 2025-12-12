import type { IdentityKeyStore } from "../state/IdentityKeyStore";
import type { SessionStore } from "../state/SessionStore";

export interface SessionBuilderWasm{
    sessionbuilder_process_prekey_bundle(
    preKeyPtr: number,
    remoteAddressPtr: number,
    sessionStore: SessionStore,
    identityKeyStore: IdentityKeyStore,
    nowMs: number
    ): void;

}
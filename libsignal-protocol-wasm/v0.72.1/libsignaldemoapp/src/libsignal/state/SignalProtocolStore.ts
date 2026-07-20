import type { SenderKeyStore } from "../protocol/groups/state/SenderKeyStore";
import type { IdentityKeyStore } from "./IdentityKeyStore";
import type { KyberPreKeyStore } from "./KyberPreKeyStore";

import type { PreKeyStore } from "./PreKeyStore";
import type { SessionStore } from "./SessionStore";
import type { SignedPreKeyStore } from "./SignedPreKeyStore";

export interface SignalProtocolStore
  extends IdentityKeyStore,
    PreKeyStore,
    SignedPreKeyStore,
    KyberPreKeyStore,
    SessionStore,
    SenderKeyStore {}

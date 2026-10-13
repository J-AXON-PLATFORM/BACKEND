package com.jaxon.collector;

import java.util.UUID;

interface SftpGoProvisioner {

    AgentActivationResponse ensureProvisioned(UUID agentId, String publicKey);
}

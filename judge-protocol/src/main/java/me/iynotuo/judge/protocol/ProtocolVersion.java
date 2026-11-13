package me.iynotuo.judge.protocol;

/**
 * Version of the internal judge protocol.
 *
 * <p>The server returns this value in the startup bootstrap config, and the agent rejects a mismatch
 * before claiming any work. The {@code protocolVersion} fields in claim and heartbeat requests are
 * currently diagnostic; the server does not enforce them.</p>
 */
public final class ProtocolVersion {

    /**
     * Current protocol version. Bump it when a change is not backward compatible.
     *
     * <p>Version 2 requires test data bundles to be ZIP archives; the legacy JSON bundle fallback
     * was removed.</p>
     */
    public static final int CURRENT = 2;

    private ProtocolVersion() {
    }
}

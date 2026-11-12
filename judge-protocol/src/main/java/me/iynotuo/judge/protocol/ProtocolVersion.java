package me.iynotuo.judge.protocol;

/**
 * Version of the internal judge protocol.
 *
 * <p>The server and a judge agent exchange this value when claiming work and sending heartbeats. A
 * mismatch is rejected instead of letting two incompatible versions disagree about the payload.</p>
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

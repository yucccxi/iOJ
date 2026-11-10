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
     */
    public static final int CURRENT = 1;

    private ProtocolVersion() {
    }
}

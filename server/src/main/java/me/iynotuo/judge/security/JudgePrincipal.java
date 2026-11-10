package me.iynotuo.judge.security;

/**
 * Authenticated remote judge agent. The key id is used for logging and for associating a node with its
 * key; the key name is only for diagnostics.
 *
 * @param keyId   judge_keys.id
 * @param keyName human-readable key name
 */
public record JudgePrincipal(Long keyId, String keyName) {
}

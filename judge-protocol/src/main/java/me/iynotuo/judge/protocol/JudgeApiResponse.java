package me.iynotuo.judge.protocol;

/**
 * Internal API response envelope. The public API uses its own response type, but both serialize to the
 * same {@code {code, message, data}} shape.
 *
 * @param code    zero on success
 * @param message human-readable message
 * @param data    payload
 */
public record JudgeApiResponse<T>(int code, String message, T data) {
}

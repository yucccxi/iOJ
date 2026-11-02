package me.iynotuo.judge.user.dto;

/**
 * Result of a code request: how long the code stays valid and when the client may ask for another
 * one. The code itself is never returned, it only travels by email.
 */
public record SendEmailCodeVO(long expiresInSeconds, long resendAfterSeconds) {
}

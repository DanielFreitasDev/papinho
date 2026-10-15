package io.github.labdev2025cmd.papinho.shared.security;

import java.util.Map;

import org.springframework.http.HttpStatus;

import io.github.labdev2025cmd.papinho.shared.error.ApiException;

/** Limite de tentativas estourado (SG-03): 429 com o tempo de espera no Problem Details. */
public class RateLimitedException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitedException(String bucket, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "rate-limited", Map.of("retryAfterSeconds", retryAfterSeconds),
                "Rate limit exceeded for " + bucket, retryAfterSeconds);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}

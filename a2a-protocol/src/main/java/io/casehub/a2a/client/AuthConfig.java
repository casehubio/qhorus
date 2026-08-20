package io.casehub.a2a.client;

public record AuthConfig(AuthType type, String tokenConfigKey, String resolvedToken) {
    public static final AuthConfig NONE = new AuthConfig(AuthType.NONE, null, null);

    public AuthConfig(AuthType type, String tokenConfigKey) {
        this(type, tokenConfigKey, null);
    }

    public enum AuthType {NONE, BEARER, API_KEY}
}

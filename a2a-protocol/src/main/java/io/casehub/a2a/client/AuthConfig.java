package io.casehub.a2a.client;

public record AuthConfig(AuthType type, String tokenConfigKey) {
    public static final AuthConfig NONE = new AuthConfig(AuthType.NONE, null);

    public enum AuthType { NONE, BEARER, API_KEY }
}

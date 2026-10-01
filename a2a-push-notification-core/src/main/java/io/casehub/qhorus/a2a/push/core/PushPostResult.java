package io.casehub.qhorus.a2a.push.core;

public record PushPostResult(boolean success, int statusCode, String error) {

    public static PushPostResult ok(int statusCode) {
        return new PushPostResult(true, statusCode, null);
    }

    public static PushPostResult fail(int statusCode, String error) {
        return new PushPostResult(false, statusCode, error);
    }
}

package io.casehub.qhorus.a2a.push;

public record PushPostResult(boolean success, int statusCode, String error) {

    static PushPostResult ok(int statusCode) {
        return new PushPostResult(true, statusCode, null);
    }

    static PushPostResult fail(int statusCode, String error) {
        return new PushPostResult(false, statusCode, error);
    }
}

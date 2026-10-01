package io.casehub.qhorus.a2a.push.core;

@FunctionalInterface
public interface HttpPoster {
    PushPostResult post(String url, String body, String authHeader);
}

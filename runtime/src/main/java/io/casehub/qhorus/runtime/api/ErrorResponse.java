package io.casehub.qhorus.runtime.api;

/** Structured error body for A2A endpoints. Package-private — internal to the api layer. */
record ErrorResponse(String error) {}

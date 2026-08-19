package io.casehub.a2a.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum A2ATaskState {
    SUBMITTED("submitted"),
    WORKING("working"),
    INPUT_REQUIRED("input-required"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELED("canceled");

    private final String wireValue;

    A2ATaskState(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELED;
    }

    public static A2ATaskState fromWireValue(String value) {
        for (A2ATaskState state : values()) {
            if (state.wireValue.equals(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown A2A task state: " + value);
    }
}

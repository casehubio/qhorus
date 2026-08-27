package io.casehub.qhorus.api.judgment;

import java.util.List;

public final class JudgmentEventKinds {
    public static final String YIELDED = "judgment_yielded";
    public static final String RESPONDED = "judgment_responded";
    public static final String VERIFIED = "judgment_verified";
    public static final String ESCALATED = "judgment_escalated";
    public static final String TOOL_NAME_PREFIX = "judgment_";

    public static final List<String> ALL = List.of(YIELDED, RESPONDED, VERIFIED, ESCALATED);
    public static final List<String> TERMINAL = List.of(VERIFIED, ESCALATED);

    private JudgmentEventKinds() {}
}

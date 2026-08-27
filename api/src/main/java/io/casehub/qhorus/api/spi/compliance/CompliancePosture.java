package io.casehub.qhorus.api.spi.compliance;

import java.util.List;

public record CompliancePosture(List<PostureEntry> entries) {

    public static final CompliancePosture EMPTY = new CompliancePosture(List.of());
}

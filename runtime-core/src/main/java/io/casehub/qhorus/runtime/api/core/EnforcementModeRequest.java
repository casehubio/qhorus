package io.casehub.qhorus.runtime.api.core;

import java.util.List;

public record EnforcementModeRequest(String mode, List<String> exclusions) {}

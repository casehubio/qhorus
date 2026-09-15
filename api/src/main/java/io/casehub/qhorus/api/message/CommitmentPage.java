package io.casehub.qhorus.api.message;

import java.util.List;

public record CommitmentPage(
    List<Commitment> items,
    boolean hasNext,
    String cursor
) {}

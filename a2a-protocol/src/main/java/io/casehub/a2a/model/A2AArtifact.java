package io.casehub.a2a.model;

import java.util.List;

public record A2AArtifact(
    String name,
    List<A2APart> parts,
    int index,
    boolean append
) {}

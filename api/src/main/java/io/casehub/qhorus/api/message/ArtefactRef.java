package io.casehub.qhorus.api.message;

public record ArtefactRef(
        String uri,
        ArtefactType type,
        String label,
        SelectionScope scope) {

    public ArtefactRef {
        if (uri == null || uri.isBlank()) throw new IllegalArgumentException("uri is required");
        if (type == null) throw new IllegalArgumentException("type is required");
    }
}

package io.casehub.qhorus.api.message;

public record SelectionScope(
        Integer startLine,
        Integer endLine,
        Integer startOffset,
        Integer endOffset,
        String selectedText) {}

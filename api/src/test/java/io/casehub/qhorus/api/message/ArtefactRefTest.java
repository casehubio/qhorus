package io.casehub.qhorus.api.message;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtefactRefTest {

    @Test
    void validRef_allFields() {
        var scope = new SelectionScope(1, 10, null, null, "selected text");
        var ref = new ArtefactRef("doc:spec.md", ArtefactType.DOCUMENT, "Design Spec", scope);
        assertThat(ref.uri()).isEqualTo("doc:spec.md");
        assertThat(ref.type()).isEqualTo(ArtefactType.DOCUMENT);
        assertThat(ref.label()).isEqualTo("Design Spec");
        assertThat(ref.scope()).isNotNull();
        assertThat(ref.scope().startLine()).isEqualTo(1);
        assertThat(ref.scope().selectedText()).isEqualTo("selected text");
    }

    @Test
    void validRef_nullLabelAndScope() {
        var ref = new ArtefactRef("https://example.com", ArtefactType.EXTERNAL, null, null);
        assertThat(ref.label()).isNull();
        assertThat(ref.scope()).isNull();
    }

    @Test
    void nullUri_rejected() {
        assertThatThrownBy(() -> new ArtefactRef(null, ArtefactType.DOCUMENT, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uri");
    }

    @Test
    void blankUri_rejected() {
        assertThatThrownBy(() -> new ArtefactRef("  ", ArtefactType.DOCUMENT, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uri");
    }

    @Test
    void nullType_rejected() {
        assertThatThrownBy(() -> new ArtefactRef("doc:spec.md", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }

    @Test
    void allArtefactTypes_serializable() {
        for (ArtefactType type : ArtefactType.values()) {
            var ref = new ArtefactRef("test:" + type.name(), type, null, null);
            assertThat(ref.type()).isEqualTo(type);
        }
        assertThat(ArtefactType.values()).hasSize(8);
    }

    @Test
    void selectionScope_allNullable() {
        var scope = new SelectionScope(null, null, null, null, null);
        assertThat(scope.startLine()).isNull();
        assertThat(scope.endLine()).isNull();
        assertThat(scope.startOffset()).isNull();
        assertThat(scope.endOffset()).isNull();
        assertThat(scope.selectedText()).isNull();
    }

    @Test
    void selectionScope_lineBased() {
        var scope = new SelectionScope(10, 20, null, null, null);
        assertThat(scope.startLine()).isEqualTo(10);
        assertThat(scope.endLine()).isEqualTo(20);
    }

    @Test
    void selectionScope_offsetBased() {
        var scope = new SelectionScope(null, null, 100, 200, "selected");
        assertThat(scope.startOffset()).isEqualTo(100);
        assertThat(scope.endOffset()).isEqualTo(200);
    }
}

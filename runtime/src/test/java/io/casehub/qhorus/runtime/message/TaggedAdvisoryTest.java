package io.casehub.qhorus.runtime.message;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaggedAdvisoryTest {

    @Test
    void flattenExtractsMessages() {
        var advisories = List.of(
                new TaggedAdvisory("TYPE_POLICY", "type violation"),
                new TaggedAdvisory("REQUEST_RESPONSE", "[REQUEST_RESPONSE] too many queries"));
        List<String> flat = advisories.stream().map(TaggedAdvisory::message).toList();
        assertThat(flat).containsExactly("type violation", "[REQUEST_RESPONSE] too many queries");
    }

    @Test
    void distinctSources() {
        var advisories = List.of(
                new TaggedAdvisory("TYPE_POLICY", "v1"),
                new TaggedAdvisory("TYPE_POLICY", "v2"),
                new TaggedAdvisory("REQUEST_RESPONSE", "v3"));
        List<String> sources = advisories.stream().map(TaggedAdvisory::source).distinct().toList();
        assertThat(sources).containsExactly("TYPE_POLICY", "REQUEST_RESPONSE");
    }
}

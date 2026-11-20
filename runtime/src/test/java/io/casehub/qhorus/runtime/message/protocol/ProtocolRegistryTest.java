package io.casehub.qhorus.runtime.message.protocol;

import io.casehub.qhorus.api.spi.ChannelProtocol;
import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.ProtocolContext;
import io.casehub.qhorus.api.spi.ProtocolResolver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolRegistryTest {

    private static ChannelProtocol stub(String name) {
        return new ChannelProtocol() {
            @Override public String protocolName() { return name; }
            @Override public List<DispatchAdvisory> evaluate(ProtocolContext ctx) { return List.of(); }
        };
    }

    private static List<ChannelProtocol> stubs(String... names) {
        List<ChannelProtocol> list = new ArrayList<>();
        for (String name : names) list.add(stub(name));
        return list;
    }

    @Test
    void forProtocols_returnsMatchedProtocols_inOrder() {
        ProtocolRegistry registry = new ProtocolRegistry(stubs("ALPHA", "BETA", "GAMMA"));

        List<ChannelProtocol> result = registry.forProtocols(List.of("GAMMA", "ALPHA"));
        assertThat(result).hasSize(2);
        assertThat(result.get(0).protocolName()).isEqualTo("GAMMA");
        assertThat(result.get(1).protocolName()).isEqualTo("ALPHA");
    }

    @Test
    void forProtocols_skipsUnknownNames() {
        ProtocolRegistry registry = new ProtocolRegistry(stubs("ALPHA"));

        List<ChannelProtocol> result = registry.forProtocols(List.of("ALPHA", "UNKNOWN"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).protocolName()).isEqualTo("ALPHA");
    }

    @Test
    void forProtocols_emptyList_returnsEmpty() {
        ProtocolRegistry registry = new ProtocolRegistry(stubs("ALPHA"));
        assertThat(registry.forProtocols(List.of())).isEmpty();
    }

    @Test
    void allNames_returnsSortedSet() {
        ProtocolRegistry registry = new ProtocolRegistry(stubs("GAMMA", "ALPHA", "BETA"));
        assertThat(registry.allNames()).containsExactly("ALPHA", "BETA", "GAMMA");
    }

    @Test
    void duplicateName_throwsIllegalStateException_atConstruction() {
        assertThatThrownBy(() -> new ProtocolRegistry(stubs("X", "X")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("X");
    }

    @Test
    void nullProtocolName_throwsIllegalStateException_atConstruction() {
        ChannelProtocol nullNamed = new ChannelProtocol() {
            @Override public String protocolName() { return null; }
            @Override public List<DispatchAdvisory> evaluate(ProtocolContext ctx) { return List.of(); }
        };
        List<ChannelProtocol> list = new ArrayList<>();
        list.add(nullNamed);

        assertThatThrownBy(() -> new ProtocolRegistry(list))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void blankProtocolName_throwsIllegalStateException_atConstruction() {
        ChannelProtocol blankNamed = new ChannelProtocol() {
            @Override public String protocolName() { return "  "; }
            @Override public List<DispatchAdvisory> evaluate(ProtocolContext ctx) { return List.of(); }
        };
        List<ChannelProtocol> list = new ArrayList<>();
        list.add(blankNamed);

        assertThatThrownBy(() -> new ProtocolRegistry(list))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void emptyRegistry_allNames_returnsEmptySet() {
        ProtocolRegistry registry = new ProtocolRegistry(stubs());
        assertThat(registry.allNames()).isEmpty();
    }

    @Test
    void forProtocols_returnsSameInstance() {
        ChannelProtocol p = stub("EXACT");
        ProtocolRegistry registry = new ProtocolRegistry(new ArrayList<>(List.of(p)));

        assertThat(registry.forProtocols(List.of("EXACT")).get(0)).isSameAs(p);
    }

    @Test
    void forProtocols_delegatesToResolver_forUnknownNames() {
        ChannelProtocol dynamicProtocol = stub("DYNAMIC");
        ProtocolResolver resolver = (names, channelId) -> {
            if (names.contains("DYNAMIC")) {return List.of(dynamicProtocol);}
            return List.of();
        };
        ProtocolRegistry registry = new ProtocolRegistry(stubs("STATIC"), resolver);

        UUID channelId = UUID.randomUUID();
        List<ChannelProtocol> result = registry.forProtocols(
                List.of("STATIC", "DYNAMIC"), channelId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).protocolName()).isEqualTo("STATIC");
        assertThat(result.get(1).protocolName()).isEqualTo("DYNAMIC");
    }

    @Test
    void forProtocols_noResolver_unknownNamesSkipped() {
        ProtocolRegistry registry  = new ProtocolRegistry(stubs("ALPHA"));
        UUID             channelId = UUID.randomUUID();
        List<ChannelProtocol> result = registry.forProtocols(
                List.of("ALPHA", "MISSING"), channelId);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).protocolName()).isEqualTo("ALPHA");
    }

    @Test
    void forProtocols_staticTakesPrecedence_overResolver() {
        ChannelProtocol  staticP  = stub("SHARED");
        ChannelProtocol  dynamicP = stub("SHARED");
        ProtocolResolver resolver = (names, channelId) -> List.of(dynamicP);
        ProtocolRegistry registry = new ProtocolRegistry(List.of(staticP), resolver);

        UUID channelId = UUID.randomUUID();
        List<ChannelProtocol> result = registry.forProtocols(
                List.of("SHARED"), channelId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(staticP);
    }
}

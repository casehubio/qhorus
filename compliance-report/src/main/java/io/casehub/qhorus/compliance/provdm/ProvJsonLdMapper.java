package io.casehub.qhorus.compliance.provdm;

import io.casehub.qhorus.runtime.ledger.CausalGraphService.CausalGraph;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.GraphEdge;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.GraphNode;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ProvJsonLdMapper {

    private ProvJsonLdMapper() {}

    public static Map<String, Object> toProvJsonLd(CausalGraph graph) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("@context", context());
        doc.put("agent", buildAgents(graph));
        doc.put("activity", buildActivities(graph));
        doc.put("wasInformedBy", buildInformedBy(graph));
        doc.put("actedOnBehalfOf", buildDelegation(graph));
        doc.put("wasDerivedFrom", buildDerivations(graph));
        return doc;
    }

    private static Map<String, String> context() {
        Map<String, String> ctx = new LinkedHashMap<>();
        ctx.put("prov", "http://www.w3.org/ns/prov#");
        ctx.put("ledger", "https://casehubio.github.io/ledger#");
        ctx.put("qhorus", "https://casehubio.github.io/qhorus#");
        ctx.put("xsd", "http://www.w3.org/2001/XMLSchema#");
        return ctx;
    }

    private static Map<String, Object> buildAgents(CausalGraph graph) {
        Map<String, Object> agents = new LinkedHashMap<>();
        for (GraphNode node : graph.nodes()) {
            String iri = "ledger:actor/" + node.actorId();
            if (!agents.containsKey(iri)) {
                Map<String, Object> agent = new LinkedHashMap<>();
                agent.put("prov:type", "prov:Agent");
                agents.put(iri, agent);
            }
        }
        return agents;
    }

    private static Map<String, Object> buildActivities(CausalGraph graph) {
        Map<String, Object> activities = new LinkedHashMap<>();
        for (GraphNode node : graph.nodes()) {
            String iri = "ledger:activity/" + node.entryId();
            Map<String, Object> activity = new LinkedHashMap<>();
            activity.put("prov:type", node.messageType());
            if (node.occurredAt() != null) {
                activity.put("prov:startTime", node.occurredAt());
            }
            activity.put("prov:wasAssociatedWith", "ledger:actor/" + node.actorId());
            activity.put("prov:atLocation", "qhorus:channel/" + node.channelId());
            activities.put(iri, activity);
        }
        return activities;
    }

    private static Map<String, Object> buildInformedBy(CausalGraph graph) {
        Map<String, Object> relations = new LinkedHashMap<>();
        for (GraphNode node : graph.nodes()) {
            if (node.causedByEntryId() != null && isCompletionType(node.messageType())) {
                String id = "ledger:activity/" + node.entryId();
                Map<String, Object> rel = new LinkedHashMap<>();
                rel.put("prov:informant", "ledger:activity/" + node.causedByEntryId());
                relations.put(id, rel);
            }
        }
        return relations;
    }

    private static Map<String, Object> buildDelegation(CausalGraph graph) {
        Map<String, Object> delegations = new LinkedHashMap<>();
        for (GraphNode node : graph.nodes()) {
            if ("HANDOFF".equals(node.messageType()) && node.causedByEntryId() != null) {
                String delegateIri = "ledger:actor/" + node.actorId();
                Map<String, Object> del = new LinkedHashMap<>();
                del.put("prov:delegate", delegateIri);
                del.put("prov:activity", "ledger:activity/" + node.entryId());
                delegations.put("delegation:" + node.entryId(), del);
            }
        }
        return delegations;
    }

    private static Map<String, Object> buildDerivations(CausalGraph graph) {
        Map<String, Object> derivations = new LinkedHashMap<>();
        for (GraphEdge edge : graph.edges()) {
            String id = "derivation:" + edge.to();
            Map<String, Object> deriv = new LinkedHashMap<>();
            deriv.put("prov:generatedEntity", "ledger:activity/" + edge.to());
            deriv.put("prov:usedEntity", "ledger:activity/" + edge.from());
            if (edge.elapsedMs() != null) {
                deriv.put("qhorus:elapsedMs", edge.elapsedMs());
            }
            derivations.put(id, deriv);
        }
        return derivations;
    }

    private static boolean isCompletionType(String messageType) {
        return "DONE".equals(messageType) || "RESPONSE".equals(messageType)
                || "FAILURE".equals(messageType) || "DECLINE".equals(messageType);
    }
}

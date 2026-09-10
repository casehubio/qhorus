package io.casehub.qhorus.graphql.governance;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.graphql.PageInfo;
import io.casehub.platform.graphql.PageInput;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.store.CommitmentReader;
import io.casehub.qhorus.graphql.dto.CommitmentFilterInput;
import io.casehub.qhorus.graphql.dto.CommitmentPage;
import io.casehub.qhorus.graphql.dto.CommitmentType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Query;

@GraphQLApi
@McpDomain("governance")
@ApplicationScoped
public class GovernanceQueryResolver {

    @Inject CommitmentReader commitmentReader;

    @Query
    @Description("List commitments with optional filtering by channel, state, obligor, or requester")
    public CommitmentPage commitments(CommitmentFilterInput filter, PageInput page) {
        int offset = page != null && page.offset() != null ? page.offset() : 0;
        int limit = page != null && page.limit() != null ? page.limit() : 20;

        List<Commitment> all = resolveCommitments(filter);
        int total = all.size();
        int end = Math.min(offset + limit, total);
        List<CommitmentType> items = offset < total
                ? all.subList(offset, end).stream().map(CommitmentType::from).toList()
                : List.of();

        boolean hasNext = end < total;
        boolean hasPrevious = offset > 0;
        return new CommitmentPage(items, new PageInfo(hasNext, hasPrevious, total, null));
    }

    private List<Commitment> resolveCommitments(CommitmentFilterInput filter) {
        if (filter == null) {
            return commitmentReader.findAllOpen();
        }
        if (filter.channelId() != null && filter.state() != null) {
            return commitmentReader.findByState(filter.state(), filter.channelId());
        }
        if (filter.channelId() != null && filter.obligor() != null) {
            return commitmentReader.findOpenByObligor(filter.obligor(), filter.channelId());
        }
        if (filter.channelId() != null && filter.requester() != null) {
            return commitmentReader.findOpenByRequester(filter.requester(), filter.channelId());
        }
        if (filter.channelId() != null) {
            return commitmentReader.findByChannel(filter.channelId());
        }
        if (filter.obligor() != null) {
            return commitmentReader.findOpenByObligor(filter.obligor());
        }
        return commitmentReader.findAllOpen();
    }
}

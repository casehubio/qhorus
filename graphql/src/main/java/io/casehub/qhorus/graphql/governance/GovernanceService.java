package io.casehub.qhorus.graphql.governance;

import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentPage;
import io.casehub.qhorus.api.message.CommitmentQuery;
import io.casehub.qhorus.api.spi.governance.GovernanceApi;
import io.casehub.qhorus.api.store.CommitmentReader;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class GovernanceService implements GovernanceApi {

    private final CommitmentReader commitmentReader;

    public GovernanceService(CommitmentReader commitmentReader) {
        this.commitmentReader = commitmentReader;
    }

    @Override
    public CommitmentPage commitments(CommitmentQuery query) {
        int offset = query != null && query.offset() != null ? query.offset() : 0;
        int limit = query != null && query.limit() != null ? query.limit() : 20;

        List<Commitment> all = resolveCommitments(query);
        int total = all.size();
        int end = Math.min(offset + limit, total);
        List<Commitment> items = offset < total
                ? all.subList(offset, end)
                : List.of();

        boolean hasNext = end < total;
        return new CommitmentPage(items, hasNext, null);
    }

    private List<Commitment> resolveCommitments(CommitmentQuery query) {
        if (query == null) {
            return commitmentReader.findAllOpen();
        }
        if (query.channelId() != null && query.state() != null) {
            return commitmentReader.findByState(query.state(), query.channelId());
        }
        if (query.channelId() != null && query.obligor() != null) {
            return commitmentReader.findOpenByObligor(query.obligor(), query.channelId());
        }
        if (query.channelId() != null && query.requester() != null) {
            return commitmentReader.findOpenByRequester(query.requester(), query.channelId());
        }
        if (query.channelId() != null) {
            return commitmentReader.findByChannel(query.channelId());
        }
        if (query.obligor() != null) {
            return commitmentReader.findOpenByObligor(query.obligor());
        }
        return commitmentReader.findAllOpen();
    }
}

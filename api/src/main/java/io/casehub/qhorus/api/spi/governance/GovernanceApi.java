package io.casehub.qhorus.api.spi.governance;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.qhorus.api.message.CommitmentPage;
import io.casehub.qhorus.api.message.CommitmentQuery;

@McpDomain("governance")
public interface GovernanceApi {

    @PlatformQuery("List commitments matching filter criteria")
    CommitmentPage commitments(CommitmentQuery query);
}

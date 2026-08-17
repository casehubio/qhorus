package io.casehub.qhorus.graphql.dto;

import io.casehub.platform.graphql.PageInfo;
import java.util.List;
import org.eclipse.microprofile.graphql.Type;

@Type("ChannelPage")
public record ChannelPage(List<ChannelType> items, PageInfo pageInfo) {}

package io.casehub.qhorus.deployment;

import java.util.List;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourcePatternsBuildItem;
import io.quarkus.hibernate.orm.deployment.AdditionalJpaModelBuildItem;

/**
 * Quarkus build-time processor for the Qhorus extension.
 */
class QhorusProcessor {

    private static final String FEATURE = "qhorus";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    NativeImageResourcePatternsBuildItem registerMigrationResources() {
        return NativeImageResourcePatternsBuildItem.builder()
                .includeGlob("db/qhorus/migration/*.sql")
                .build();
    }

    @BuildStep
    List<AdditionalJpaModelBuildItem> registerEntities() {
        return List.of(
                new AdditionalJpaModelBuildItem("io.casehub.qhorus.runtime.channel.ChannelEntity"),
                new AdditionalJpaModelBuildItem("io.casehub.qhorus.runtime.channel.ChannelConnectorBindingEntity"),
                new AdditionalJpaModelBuildItem("io.casehub.qhorus.runtime.channel.ChannelMembershipEntity"),
                new AdditionalJpaModelBuildItem("io.casehub.qhorus.runtime.channel.ChannelSummaryEntity"),
                new AdditionalJpaModelBuildItem("io.casehub.qhorus.runtime.channel.SpaceEntity"),
                new AdditionalJpaModelBuildItem("io.casehub.qhorus.runtime.channel.ThreadSummaryEntity"),
                new AdditionalJpaModelBuildItem("io.casehub.qhorus.runtime.data.ArtefactClaimEntity"),
                new AdditionalJpaModelBuildItem("io.casehub.qhorus.runtime.data.SharedDataEntity"),
                new AdditionalJpaModelBuildItem("io.casehub.qhorus.runtime.gateway.DeliveryCursorEntity"),
                new AdditionalJpaModelBuildItem("io.casehub.qhorus.runtime.instance.CapabilityEntity"),
                new AdditionalJpaModelBuildItem("io.casehub.qhorus.runtime.instance.ExternalAgentBindingEntity"),
                new AdditionalJpaModelBuildItem("io.casehub.qhorus.runtime.instance.InstanceEntity"),
                new AdditionalJpaModelBuildItem("io.casehub.qhorus.runtime.message.CommitmentEntity"),
                new AdditionalJpaModelBuildItem("io.casehub.qhorus.runtime.message.MessageEntity"),
                new AdditionalJpaModelBuildItem("io.casehub.qhorus.runtime.message.ReactionEntity"),
                new AdditionalJpaModelBuildItem("io.casehub.qhorus.runtime.message.TopicEntity"),
                new AdditionalJpaModelBuildItem("io.casehub.qhorus.runtime.watchdog.WatchdogEntity"),
                new AdditionalJpaModelBuildItem("io.casehub.qhorus.runtime.ledger.MessageLedgerEntry")
        );
    }

}

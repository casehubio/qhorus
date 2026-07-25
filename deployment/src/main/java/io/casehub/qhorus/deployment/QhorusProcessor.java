package io.casehub.qhorus.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourcePatternsBuildItem;

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
                // db/ledger/migration/*.sql — self-registered by LedgerProcessor since ledger#99
                .build();
    }
}

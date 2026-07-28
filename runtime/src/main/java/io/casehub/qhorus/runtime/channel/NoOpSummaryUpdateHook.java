package io.casehub.qhorus.runtime.channel;

import io.casehub.qhorus.api.spi.SummaryResult;
import io.casehub.qhorus.api.spi.SummaryUpdateContext;
import io.casehub.qhorus.api.spi.SummaryUpdateHook;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpSummaryUpdateHook implements SummaryUpdateHook {
    @Override
    public SummaryResult update(SummaryUpdateContext context) {
        return context.previousResult() != null
               ? context.previousResult()
               : SummaryResult.ofText("");
    }
}

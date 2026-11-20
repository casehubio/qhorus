package io.casehub.qhorus.playbook.bridge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
public class DeadlineSweepScheduler {

    @Inject
    ProtocolDeadlineScheduler deadlineScheduler;

    @Scheduled(every = "60s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void sweepDeadlines() {
        deadlineScheduler.sweep();
    }
}

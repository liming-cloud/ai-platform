package com.aiplatform.knowledge.application.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class KnowledgeDocumentMaintenanceSchedulerTest {
    @Test
    void scheduledPollDelegatesToTheDurableMaintenanceWorker() throws Exception {
        KnowledgeDocumentMaintenanceWorker worker = mock(KnowledgeDocumentMaintenanceWorker.class);
        KnowledgeDocumentMaintenanceScheduler scheduler = new KnowledgeDocumentMaintenanceScheduler(worker);

        scheduler.recoverMaintenance();

        verify(worker).drainAvailable();
        Method poll = KnowledgeDocumentMaintenanceScheduler.class.getMethod("recoverMaintenance");
        Scheduled scheduled = poll.getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString()).contains("aip.knowledge.maintenance.poll-delay-ms");
    }
}

package com.cacanode.api.recruitment.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecruitmentFoundationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RecruitmentRabbitConfig.class);

    @Test
    void flagsDefaultOffAndInvalidCombinationsAreRejected() {
        RecruitmentProperties defaults = new RecruitmentProperties(
                false, false, false, false, false, false, false);
        assertTrue(defaults.isMasterFlagValid());
        assertTrue(defaults.isMessagingDependencyValid());
        assertTrue(defaults.isRecordingDependencyValid());
        assertTrue(defaults.isAutomationDependencyValid());
        assertFalse(new RecruitmentProperties(
                false, true, false, false, false, false, false).isMasterFlagValid());
        assertFalse(new RecruitmentProperties(
                true, false, false, false, true, false, false).isMessagingDependencyValid());
        assertFalse(new RecruitmentProperties(
                true, true, false, false, false, false, true).isRecordingDependencyValid());
        assertFalse(new RecruitmentProperties(
                true, false, false, true, false, false, false).isAutomationDependencyValid());
    }

    @Test
    void rabbitTopologyIsAbsentWhenFlagsAreOff() {
        contextRunner.run(context -> {
            assertTrue(context.getBeansOfType(TopicExchange.class).isEmpty());
            assertTrue(context.getBeansOfType(Queue.class).isEmpty());
        });
    }

    @Test
    void rabbitTopologyHasExactDurableNamesWhenEnabled() {
        contextRunner.withPropertyValues(
                        "app.recruitment.enabled=true",
                        "app.recruitment.messaging-enabled=true")
                .run(context -> {
                    assertTrue(context.getBeansOfType(TopicExchange.class).values().stream()
                            .map(TopicExchange::getName)
                            .toList().containsAll(java.util.List.of(
                                    RecruitmentRabbitTopology.INTERVIEW_EXCHANGE,
                                    RecruitmentRabbitTopology.DEAD_LETTER_EXCHANGE,
                                    RecruitmentRabbitTopology.RECORDING_OPERATION_EXCHANGE,
                                    RecruitmentRabbitTopology.RECORDING_OPERATION_DEAD_LETTER_EXCHANGE)));
                    assertTrue(context.getBeansOfType(Queue.class).values().stream()
                            .allMatch(Queue::isDurable));
                    assertTrue(context.getBeansOfType(Queue.class).values().stream()
                            .map(Queue::getName)
                            .toList().containsAll(java.util.List.of(
                                    RecruitmentRabbitTopology.RESUME_ANALYSIS_QUEUE,
                                    RecruitmentRabbitTopology.INTERVIEW_EVENTS_QUEUE,
                                    RecruitmentRabbitTopology.RECORDING_OPERATION_QUEUE,
                                    RecruitmentRabbitTopology.RESUME_ANALYSIS_DLQ,
                                    RecruitmentRabbitTopology.INTERVIEW_EVENTS_DLQ,
                                    RecruitmentRabbitTopology.RECORDING_OPERATION_DLQ)));
                });
    }
}

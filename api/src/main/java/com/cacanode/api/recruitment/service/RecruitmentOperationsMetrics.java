package com.cacanode.api.recruitment.service;

import com.cacanode.api.recruitment.config.RecruitmentCostProperties;
import com.cacanode.api.recruitment.config.RecruitmentRabbitTopology;
import com.cacanode.api.recruitment.query.RecruitmentOperationsQuery;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
@ConditionalOnProperty(prefix="app.recruitment",name="enabled",havingValue="true")
public class RecruitmentOperationsMetrics {
    private final RecruitmentOperationsQuery operations;private final AmqpAdmin rabbit;private final RecruitmentCostProperties rates;
    private final AtomicReference<Double> estimatedUsd=new AtomicReference<>(0d);
    private final Map<String,AtomicLong> stageCounts=new LinkedHashMap<>();
    private final Map<String,AtomicLong> ready=new LinkedHashMap<>(),consumers=new LinkedHashMap<>(),dlq=new LinkedHashMap<>();
    private final AtomicLong integrityViolations=new AtomicLong();

    public RecruitmentOperationsMetrics(RecruitmentOperationsQuery operations,AmqpAdmin rabbit,RecruitmentCostProperties rates,MeterRegistry registry) {
        this.operations=operations;this.rabbit=rabbit;this.rates=rates;
        Gauge.builder("recruitment.estimated.cost.usd",estimatedUsd,AtomicReference::get)
                .tag("rate_card",rates.rateCardVersion()).description("Operational estimate; not billing authority").register(registry);
        for(String stage:new String[]{"OFF","INTERNAL","PILOT","GA"}) {AtomicLong value=new AtomicLong();stageCounts.put(stage,value);Gauge.builder("recruitment.activation.tenants",value,AtomicLong::doubleValue).tag("stage",stage).register(registry);}
        for(String queue:queues()) {
            AtomicLong qReady=new AtomicLong(),qConsumers=new AtomicLong(),qDlq=new AtomicLong();ready.put(queue,qReady);consumers.put(queue,qConsumers);dlq.put(queue,qDlq);
            Gauge.builder("recruitment.queue.messages.ready",qReady,AtomicLong::doubleValue).tag("queue",queue).register(registry);
            Gauge.builder("recruitment.queue.consumers",qConsumers,AtomicLong::doubleValue).tag("queue",queue).register(registry);
            Gauge.builder("recruitment.queue.dlq.depth",qDlq,AtomicLong::doubleValue).tag("queue",queue).register(registry);
        }
        Gauge.builder("recruitment.tenant.integrity.violations",integrityViolations,AtomicLong::doubleValue).register(registry);
    }

    @Scheduled(fixedDelayString="${app.recruitment.metrics-collection-ms:30000}")
    public void collect() {
        for(var entry:stageCounts.entrySet())entry.getValue().set(operations.activationTenantCount(entry.getKey()));
        integrityViolations.set(operations.tenantIntegrityViolationCount());
        collectQueues();estimatedUsd.set(cost().doubleValue());
    }

    private void collectQueues() {
        for(String queue:queues())try {
            java.util.Properties properties=rabbit.getQueueProperties(queue);
            long count=number(properties,"QUEUE_MESSAGE_COUNT");long consumerCount=number(properties,"QUEUE_CONSUMER_COUNT");
            ready.get(queue).set(count);consumers.get(queue).set(consumerCount);dlq.get(queue).set(queue.endsWith(".dlq")?count:0);
        } catch(RuntimeException exception){ready.get(queue).set(-1);consumers.get(queue).set(-1);dlq.get(queue).set(-1);}
    }

    private BigDecimal cost() {
        RecruitmentOperationsQuery.CostInputs inputs=operations.costInputs();
        return inputs.callSeconds().divide(BigDecimal.valueOf(60),12,RoundingMode.HALF_UP).multiply(rates.twilioCallUsdPerMinute())
                .add(inputs.recordingSeconds().divide(BigDecimal.valueOf(60),12,RoundingMode.HALF_UP).multiply(rates.recordingUsdPerMinute()))
                .add(inputs.speechCharacters().divide(BigDecimal.valueOf(1_000_000),12,RoundingMode.HALF_UP).multiply(rates.cartesiaUsdPerMillionCharacters()))
                .add(inputs.modelTokens().divide(BigDecimal.valueOf(1_000_000),12,RoundingMode.HALF_UP).multiply(rates.modelUsdPerMillionTokens()));
    }
    private static long number(java.util.Properties values,String key){if(values==null)return -1;Object value=values.get(key);return value instanceof Number number?number.longValue():-1;}
    private static String[] queues(){return new String[]{RecruitmentRabbitTopology.RESUME_ANALYSIS_QUEUE,RecruitmentRabbitTopology.INTERVIEW_EVENTS_QUEUE,
            RecruitmentRabbitTopology.RECORDING_OPERATION_QUEUE,RecruitmentRabbitTopology.RESUME_ANALYSIS_DLQ,
            RecruitmentRabbitTopology.INTERVIEW_EVENTS_DLQ,RecruitmentRabbitTopology.RECORDING_OPERATION_DLQ};}
}

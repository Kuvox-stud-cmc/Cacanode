package com.cacanode.api.recruitment.config;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

@Component("recruitmentInfrastructure")
public class RecruitmentInfrastructureReadiness implements HealthIndicator {
    private final RecruitmentProperties properties;private final StringRedisTemplate redis;private final ConnectionFactory rabbit;
    public RecruitmentInfrastructureReadiness(RecruitmentProperties properties,StringRedisTemplate redis,ConnectionFactory rabbit){this.properties=properties;this.redis=redis;this.rabbit=rabbit;}
    @Override public Health health() {
        if(!properties.enabled()||!properties.messagingEnabled()&&!properties.callingEnabled())return Health.up().withDetail("required",false).build();
        try {
            RedisState state=redis.execute((RedisConnection connection)->{
                Properties persistence=connection.serverCommands().info("persistence");
                Object policy=connection.execute("CONFIG","GET".getBytes(StandardCharsets.UTF_8),"maxmemory-policy".getBytes(StandardCharsets.UTF_8));
                Object fsync=connection.execute("CONFIG","GET".getBytes(StandardCharsets.UTF_8),"appendfsync".getBytes(StandardCharsets.UTF_8));
                return new RedisState("1".equals(persistence.getProperty("aof_enabled")),text(policy).contains("noeviction"),text(fsync).contains("everysec"));
            });
            if(state==null||!state.aof||!state.noEviction||!state.everySecond)return Health.down().withDetail("redisDurability",state).build();
            try(Connection connection=rabbit.createConnection();Channel channel=connection.createChannel(false)) {
                for(String queue:List.of(RecruitmentRabbitTopology.RESUME_ANALYSIS_QUEUE,RecruitmentRabbitTopology.INTERVIEW_EVENTS_QUEUE,
                        RecruitmentRabbitTopology.RECORDING_OPERATION_QUEUE,RecruitmentRabbitTopology.RESUME_ANALYSIS_DLQ,
                        RecruitmentRabbitTopology.INTERVIEW_EVENTS_DLQ,RecruitmentRabbitTopology.RECORDING_OPERATION_DLQ))channel.queueDeclarePassive(queue);
            }
            return Health.up().withDetail("redisAof",true).withDetail("redisAppendFsync","everysec")
                    .withDetail("redisEvictionPolicy","noeviction").withDetail("rabbitTopology",true).build();
        } catch(Exception exception){return Health.down().withDetail("errorCode",exception.getClass().getSimpleName()).build();}
    }
    private static String text(Object value){
        if(value instanceof byte[] bytes)return new String(bytes,StandardCharsets.UTF_8);
        if(value instanceof Iterable<?> items){StringBuilder out=new StringBuilder();for(Object item:items)out.append(text(item)).append(' ');return out.toString();}
        return String.valueOf(value);
    }
    private record RedisState(boolean aof,boolean noEviction,boolean everySecond) {}
}

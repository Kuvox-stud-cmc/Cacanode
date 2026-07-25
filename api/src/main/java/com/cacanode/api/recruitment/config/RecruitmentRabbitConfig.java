package com.cacanode.api.recruitment.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
        prefix = "app.recruitment",
        name = {"enabled", "messaging-enabled"},
        havingValue = "true")
public class RecruitmentRabbitConfig {
    @Bean
    SimpleRabbitListenerContainerFactory recruitmentInterviewListenerContainerFactory(
            ObjectProvider<SimpleRabbitListenerContainerFactoryConfigurer> configurers,
            ObjectProvider<ConnectionFactory> connections) {
        SimpleRabbitListenerContainerFactory factory=new SimpleRabbitListenerContainerFactory();
        SimpleRabbitListenerContainerFactoryConfigurer configurer=configurers.getIfAvailable();
        ConnectionFactory connectionFactory=connections.getIfAvailable();
        if(configurer!=null&&connectionFactory!=null)configurer.configure(factory,connectionFactory);
        else if(connectionFactory!=null)factory.setConnectionFactory(connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless().maxRetries(2)
                .recoverer(new RejectAndDontRequeueRecoverer()).build());
        return factory;
    }
    @Bean
    TopicExchange interviewExchange() {
        return ExchangeBuilder.topicExchange(RecruitmentRabbitTopology.INTERVIEW_EXCHANGE)
                .durable(true).build();
    }

    @Bean
    TopicExchange interviewDeadLetterExchange() {
        return ExchangeBuilder.topicExchange(RecruitmentRabbitTopology.DEAD_LETTER_EXCHANGE)
                .durable(true).build();
    }

    @Bean
    TopicExchange recruitmentRecordingOperationExchange() {
        return ExchangeBuilder.topicExchange(RecruitmentRabbitTopology.RECORDING_OPERATION_EXCHANGE)
                .durable(true).build();
    }

    @Bean
    TopicExchange recruitmentRecordingOperationDeadLetterExchange() {
        return ExchangeBuilder.topicExchange(
                        RecruitmentRabbitTopology.RECORDING_OPERATION_DEAD_LETTER_EXCHANGE)
                .durable(true).build();
    }

    @Bean
    Queue interviewResumeAnalysisQueue() {
        return QueueBuilder.durable(RecruitmentRabbitTopology.RESUME_ANALYSIS_QUEUE)
                .deadLetterExchange(RecruitmentRabbitTopology.DEAD_LETTER_EXCHANGE).build();
    }

    @Bean
    Queue recruitmentInterviewEventsQueue() {
        return QueueBuilder.durable(RecruitmentRabbitTopology.INTERVIEW_EVENTS_QUEUE)
                .deadLetterExchange(RecruitmentRabbitTopology.DEAD_LETTER_EXCHANGE).build();
    }

    @Bean
    Queue recruitmentRecordingOperationQueue() {
        return QueueBuilder.durable(RecruitmentRabbitTopology.RECORDING_OPERATION_QUEUE)
                .deadLetterExchange(RecruitmentRabbitTopology.RECORDING_OPERATION_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RecruitmentRabbitTopology.RECORDING_OPERATION_REQUESTED)
                .build();
    }

    @Bean
    Queue interviewResumeAnalysisDeadLetterQueue() {
        return QueueBuilder.durable(RecruitmentRabbitTopology.RESUME_ANALYSIS_DLQ).build();
    }

    @Bean
    Queue recruitmentInterviewEventsDeadLetterQueue() {
        return QueueBuilder.durable(RecruitmentRabbitTopology.INTERVIEW_EVENTS_DLQ).build();
    }

    @Bean
    Queue recruitmentRecordingOperationDeadLetterQueue() {
        return QueueBuilder.durable(RecruitmentRabbitTopology.RECORDING_OPERATION_DLQ).build();
    }

    @Bean
    Binding interviewResumeAnalysisBinding(Queue interviewResumeAnalysisQueue, TopicExchange interviewExchange) {
        return BindingBuilder.bind(interviewResumeAnalysisQueue).to(interviewExchange)
                .with(RecruitmentRabbitTopology.RESUME_ANALYSIS_REQUESTED);
    }

    @Bean
    Binding resumeAnalysisOutcomeBinding(Queue recruitmentInterviewEventsQueue, TopicExchange interviewExchange) {
        return eventBinding(recruitmentInterviewEventsQueue, interviewExchange,
                RecruitmentRabbitTopology.RESUME_ANALYSIS_OUTCOME);
    }

    @Bean
    Binding finalizedTurnBinding(Queue recruitmentInterviewEventsQueue, TopicExchange interviewExchange) {
        return eventBinding(recruitmentInterviewEventsQueue, interviewExchange,
                RecruitmentRabbitTopology.TURN_FINALIZED);
    }

    @Bean
    Binding interviewCompletedBinding(Queue recruitmentInterviewEventsQueue, TopicExchange interviewExchange) {
        return eventBinding(recruitmentInterviewEventsQueue, interviewExchange,
                RecruitmentRabbitTopology.SESSION_COMPLETED);
    }

    @Bean
    Binding interviewFailedBinding(Queue recruitmentInterviewEventsQueue, TopicExchange interviewExchange) {
        return eventBinding(recruitmentInterviewEventsQueue, interviewExchange,
                RecruitmentRabbitTopology.SESSION_FAILED);
    }

    @Bean
    Binding interviewProviderUsageBinding(Queue recruitmentInterviewEventsQueue, TopicExchange interviewExchange) {
        return eventBinding(recruitmentInterviewEventsQueue, interviewExchange,
                RecruitmentRabbitTopology.PROVIDER_USAGE);
    }

    @Bean
    Binding recruitmentRecordingOperationBinding(
            Queue recruitmentRecordingOperationQueue,
            TopicExchange recruitmentRecordingOperationExchange) {
        return BindingBuilder.bind(recruitmentRecordingOperationQueue)
                .to(recruitmentRecordingOperationExchange)
                .with(RecruitmentRabbitTopology.RECORDING_OPERATION_REQUESTED);
    }

    @Bean
    Binding interviewResumeAnalysisDlqBinding(
            Queue interviewResumeAnalysisDeadLetterQueue, TopicExchange interviewDeadLetterExchange) {
        return BindingBuilder.bind(interviewResumeAnalysisDeadLetterQueue)
                .to(interviewDeadLetterExchange)
                .with(RecruitmentRabbitTopology.RESUME_ANALYSIS_REQUESTED);
    }

    @Bean
    Binding recruitmentInterviewEventsDlqBinding(
            Queue recruitmentInterviewEventsDeadLetterQueue, TopicExchange interviewDeadLetterExchange) {
        return BindingBuilder.bind(recruitmentInterviewEventsDeadLetterQueue)
                .to(interviewDeadLetterExchange).with("interview.#");
    }


    @Bean
    Binding recruitmentRecordingOperationDlqBinding(
            Queue recruitmentRecordingOperationDeadLetterQueue,
            TopicExchange recruitmentRecordingOperationDeadLetterExchange) {
        return BindingBuilder.bind(recruitmentRecordingOperationDeadLetterQueue)
                .to(recruitmentRecordingOperationDeadLetterExchange)
                .with(RecruitmentRabbitTopology.RECORDING_OPERATION_REQUESTED);
    }

    private Binding eventBinding(Queue queue, TopicExchange exchange, String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}

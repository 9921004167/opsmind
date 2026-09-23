package com.opsmind.core.config;

import com.opsmind.core.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the topics Phase 1 actually produces/consumes as Kafka NewTopic beans so
 * Spring Kafka's KafkaAdmin creates them automatically on startup - no manual
 * `kafka-topics.sh` step required to run this locally.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic alertReceivedTopic() {
        return TopicBuilder.name(KafkaTopics.ALERT_RECEIVED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic incidentCreatedTopic() {
        return TopicBuilder.name(KafkaTopics.INCIDENT_CREATED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic incidentStatusChangedTopic() {
        return TopicBuilder.name(KafkaTopics.INCIDENT_STATUS_CHANGED).partitions(3).replicas(1).build();
    }
}

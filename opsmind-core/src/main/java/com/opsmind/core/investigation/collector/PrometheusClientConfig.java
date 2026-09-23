package com.opsmind.core.investigation.collector;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PrometheusClientConfig {

    @Bean
    public RestClient prometheusRestClient(@Value("${opsmind.evidence.prometheus.base-url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}

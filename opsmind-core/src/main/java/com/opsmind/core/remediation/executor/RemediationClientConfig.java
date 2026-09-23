package com.opsmind.core.remediation.executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RemediationClientConfig {

    /** No baseUrl - each RunbookDefinition.executionEndpoint is a full URL,
     *  since different runbooks target different ecommerce services/ports. */
    @Bean
    public RestClient remediationRestClient() {
        return RestClient.builder().build();
    }
}

package com.opsmind.core.investigation.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class GeminiClientConfig {

    @Bean
    public RestClient geminiRestClient() {
        // Base URL only - the API key is NEVER put in a bean definition or logged;
        // it is read fresh from configuration on each call (see
        // GeminiAIInvestigator) and appended as a query param, matching Gemini's
        // own documented auth mechanism for generateContent.
        return RestClient.builder().baseUrl("https://generativelanguage.googleapis.com").build();
    }
}

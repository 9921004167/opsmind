package com.opsmind.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.acme.incident.memory.config.IncidentMemoryProperties;

@SpringBootApplication
@ComponentScan(basePackages = {"com.opsmind.core", "com.acme.incident.memory"})
@EnableScheduling
@EnableConfigurationProperties(IncidentMemoryProperties.class)
public class OpsMindCoreApplication {
    public static void main(String[] args) {
        System.out.println(">>> BUILD-MARKER-9F3K2A STARTING <<<");
        SpringApplication.run(OpsMindCoreApplication.class, args);
    }

    @org.springframework.context.annotation.Bean
    org.springframework.boot.CommandLineRunner debugBeanCheck(org.springframework.context.ApplicationContext ctx) {
        return args -> {
            System.out.println(">>> incidentMemoryEnqueuer bean present: " +
                ctx.containsBean("incidentMemoryEnqueuer"));
            System.out.println(">>> Beans found containing 'incidentMemory': " +
                java.util.Arrays.toString(
                    java.util.Arrays.stream(ctx.getBeanDefinitionNames())
                        .filter(n -> n.toLowerCase().contains("incidentmemory"))
                        .toArray()));
        };
    }
}
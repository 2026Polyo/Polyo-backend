package com.polyo.polyo.credit.web;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CreditOpenApiConfiguration {
    @Bean
    public GroupedOpenApi creditOpenApi() {
        return GroupedOpenApi.builder().group("credit").pathsToMatch("/api/credit/v11/**").build();
    }
}

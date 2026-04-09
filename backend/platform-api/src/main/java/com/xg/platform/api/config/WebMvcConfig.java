package com.xg.platform.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class WebMvcConfig {

    @Bean
    CurrentUserIdArgumentResolver currentUserIdArgumentResolver(PlatformProperties properties) {
        return new CurrentUserIdArgumentResolver(properties.getDevUserId());
    }

    @Bean
    WebMvcConfigurer currentUserIdWebMvcConfigurer(CurrentUserIdArgumentResolver resolver) {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(resolver);
            }
        };
    }
}

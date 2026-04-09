package com.xg.platform.api.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.xg.platform.api.persistence.mybatisplus.typehandler.InstantTimestampTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

@Configuration(proxyBeanMethods = false)
@MapperScan("com.xg.platform.api.persistence.mybatisplus.mapper")
public class MybatisPersistenceConfig {

    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        return new MybatisPlusInterceptor();
    }

    @Bean
    ConfigurationCustomizer mybatisConfigurationCustomizer() {
        return configuration -> {
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.getTypeHandlerRegistry()
                    .register(Instant.class, JdbcType.TIMESTAMP, InstantTimestampTypeHandler.class);
            configuration.getTypeHandlerRegistry()
                    .register(Instant.class, InstantTimestampTypeHandler.class);
        };
    }
}

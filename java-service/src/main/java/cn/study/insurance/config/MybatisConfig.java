package cn.study.insurance.config;

import cn.study.insurance.common.UuidTypeHandler;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * MyBatis 配置。
 *
 * MyBatis 3.5 默认没有 UUID 类型的处理器，而 PostgreSQL 的 uuid 列
 * 用 java.util.UUID 接收时必须有一个。这里通过 ConfigurationCustomizer
 * 在 MyBatis 配置构建阶段注册，确保早于 Mapper 初始化生效。
 */
@Configuration
public class MybatisConfig {

    @Bean
    public ConfigurationCustomizer uuidTypeHandlerCustomizer() {
        return configuration -> {
            var registry = configuration.getTypeHandlerRegistry();
            registry.register(UUID.class, new UuidTypeHandler());
        };
    }
}

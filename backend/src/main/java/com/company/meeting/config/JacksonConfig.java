package com.company.meeting.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * 把 Java Long 和 long 序列化为 JSON 字符串。
 * 避免 BIGINT 超过 JavaScript 安全整数范围时 ID 失真；Long 统计值同样受此配置影响。
 */
@Configuration
public class JacksonConfig {
    /**
     * 同时注册 Long 与 long 类型的字符串序列化器。
     */
    @Bean
    public SimpleModule longAsStringModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, new JsonSerializer<>() {
            /**
             * 长整型写为 JSON 字符串，避免浏览器大整数失真。
             */
            @Override
            public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value == null ? null : value.toString());
            }
        });
        module.addSerializer(Long.TYPE, new JsonSerializer<>() {
            /**
             * 长整型写为 JSON 字符串，避免浏览器大整数失真。
             */
            @Override
            public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value == null ? null : value.toString());
            }
        });
        return module;
    }
}

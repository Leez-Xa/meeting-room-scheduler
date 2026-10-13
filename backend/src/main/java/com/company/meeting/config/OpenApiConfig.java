package com.company.meeting.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    public OpenApiConfig() {
        SpringDocUtils.getConfig().replaceWithClass(Long.class, String.class);
        SpringDocUtils.getConfig().replaceWithClass(long.class, String.class);
    }
    @Bean
    public OpenAPI meetingOpenApi() {
        return new OpenAPI().info(new Info().title("企业会议室智能预约调度系统 API")
            .version("MVP")
            .description("北京时间；BIGINT 标识及 Long 总数以字符串传输。先访问 /api/v1/auth/csrf，登录后使用 HttpOnly 会话 Cookie。写操作附带 X-CSRF-TOKEN。状态只通过提交、审批、取消操作变更。"));
    }
}

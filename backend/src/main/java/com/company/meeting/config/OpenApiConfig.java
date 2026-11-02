package com.company.meeting.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 元数据与类型适配，使 Long 字符串声明与实际输出一致。
 * 描述会话和 CSRF 调用约定，文档的访问授权仍由安全过滤链控制。
 */
@Configuration
public class OpenApiConfig {
    public OpenApiConfig() {
        SpringDocUtils.getConfig().replaceWithClass(Long.class, String.class);
        SpringDocUtils.getConfig().replaceWithClass(long.class, String.class);
    }
    /**
     * 定义接口说明与调用约定，不改变实际授权。
     */
    @Bean
    public OpenAPI meetingOpenApi() {
        return new OpenAPI().info(new Info().title("企业会议室智能预约调度系统 API")
            .version("MVP")
            .description("北京时间；BIGINT 标识及 Long 总数以字符串传输。先访问 /api/v1/auth/csrf，登录后使用 HttpOnly 会话 Cookie。写操作附带 X-CSRF-TOKEN。状态只通过提交、审批、取消操作变更。"));
    }
}

package com.company.meeting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 后端应用入口。
 * 启动组件扫描，并启用预约状态推进所需的定时任务与声明式事务。
 * 前端独立运行，本应用只提供业务 API，不生成网页。
 */
@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement
public class MeetingApplication {
    /** 启动后端容器，命令行参数可选择数据库 profile 或覆盖端口。 */
    public static void main(String[] args) {
        SpringApplication.run(MeetingApplication.class, args);
    }
}

package com.aiplatform.foundation.queryruntime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 在线可信查询运行入口，与管理写入和摄取执行保持进程隔离。
 */
@SpringBootApplication
public class QueryRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(QueryRuntimeApplication.class, args);
    }
}

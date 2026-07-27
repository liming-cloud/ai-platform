package com.aiplatform.foundation.queryruntime;

import com.aiplatform.foundation.bootstrap.query.QueryBootstrapConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 在线可信查询运行入口，与管理写入和摄取执行保持进程隔离。
 */
@SpringBootApplication
@Import(QueryBootstrapConfiguration.class)
public class QueryRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(QueryRuntimeApplication.class, args);
    }
}

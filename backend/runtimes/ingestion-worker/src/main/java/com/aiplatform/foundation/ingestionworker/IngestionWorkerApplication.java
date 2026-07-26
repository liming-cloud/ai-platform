package com.aiplatform.foundation.ingestionworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 数据摄取运行入口，独立承载连接器、解析、向量化和索引生命周期任务。
 */
@SpringBootApplication
public class IngestionWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestionWorkerApplication.class, args);
    }
}

package com.aiplatform.foundation.ingestionworker;

import com.aiplatform.foundation.bootstrap.ingestion.IngestionBootstrapConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 数据摄取运行入口，独立承载连接器、解析、向量化和索引生命周期任务。
 */
@SpringBootApplication
@Import(IngestionBootstrapConfiguration.class)
public class IngestionWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestionWorkerApplication.class, args);
    }
}

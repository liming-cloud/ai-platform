package com.aiplatform.foundation.controlplane;

import com.aiplatform.foundation.bootstrap.controlplane.ControlPlaneBootstrapConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 管理控制面运行入口，只装配管理写入、治理和发布能力。
 */
@SpringBootApplication
@Import(ControlPlaneBootstrapConfiguration.class)
public class ControlPlaneApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControlPlaneApplication.class, args);
    }
}

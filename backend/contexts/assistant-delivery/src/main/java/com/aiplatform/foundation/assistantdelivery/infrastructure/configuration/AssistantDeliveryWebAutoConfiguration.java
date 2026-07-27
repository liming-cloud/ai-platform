package com.aiplatform.foundation.assistantdelivery.infrastructure.configuration;

import com.aiplatform.foundation.assistantdelivery.interfaces.web.assistant.controller.AssistantController;
import com.aiplatform.foundation.assistantdelivery.interfaces.web.assistant.controller.AssistantVersionController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * 显式注册控制面助手入口。
 */
@AutoConfiguration(after = AssistantDeliveryManagementAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.control-plane.enabled",
        havingValue = "true")
@Import({
    AssistantController.class,
    AssistantVersionController.class
})
public class AssistantDeliveryWebAutoConfiguration {
}

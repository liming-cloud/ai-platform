package com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.port.out;

import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.dto.AssistantTestRunTerminalCommit;

/**
 * 原子提交草稿助手测试终态及其内部调用轨迹。
 */
public interface AssistantTestRunTerminalCommitter {

    void commit(AssistantTestRunTerminalCommit terminal);
}

package com.aiplatform.foundation.provider.deterministic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatMessage;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatRequest;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatRole;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicChatModelTest {

    @Test
    void 应从多条商品证据中直接回答销量最高项() {
        var deployment = new ChatDeploymentRef(
                320000000000000001L,
                2);
        var model = new DeterministicChatModel(deployment);

        var result = model.chat(new ChatRequest(
                deployment,
                List.of(
                        new ChatMessage(ChatRole.SYSTEM, "只依据授权证据回答。"),
                        new ChatMessage(ChatRole.USER, """
                                问题：最近 30 天卖得最多的是哪件商品？

                                授权证据：
                                [证据1] [记录: store-product-002]
                                名称: 原味燕麦脆片
                                最近 30 日销量: 173 袋
                                [证据2] [记录: store-product-001]
                                名称: 青柚气泡水
                                最近 30 日销量: 428 瓶
                                """))));

        assertEquals("青柚气泡水最近 30 日销量最高，为 428 瓶。", result.content());
        assertFalse(result.content().contains("授权证据"));
        assertFalse(result.content().contains("演示模型已收到"));
        assertEquals("STOP", result.finishReason());
    }

    @Test
    void 应根据问题只呈现相关字段而不回显提示词() {
        var deployment = new ChatDeploymentRef(
                320000000000000001L,
                2);
        var model = new DeterministicChatModel(deployment);

        var result = model.chat(new ChatRequest(
                deployment,
                List.of(new ChatMessage(ChatRole.USER, """
                        问题：青柚气泡水在哪里，多少钱？

                        授权证据：
                        [证据1] [记录: store-product-001]
                        名称: 青柚气泡水
                        位置: 一层 A 区 03 号货架第二层
                        含税单价: 6.80 元/瓶
                        库存状态: 充足
                        """))));

        assertTrue(result.content().contains("一层 A 区 03 号货架第二层"));
        assertTrue(result.content().contains("6.80 元/瓶"));
        assertFalse(result.content().contains("库存状态"));
        assertFalse(result.content().contains("问题："));
    }

    @Test
    void 应将无法确认库存的问题路由到人工服务兜底() {
        var deployment = new ChatDeploymentRef(
                320000000000000001L,
                2);
        var model = new DeterministicChatModel(deployment);

        var result = model.chat(new ChatRequest(
                deployment,
                List.of(new ChatMessage(ChatRole.USER, """
                        问题：机器人不能确认实时库存时怎么办？

                        授权证据：
                        [证据1] [记录: store-product-001]
                        名称: 青柚气泡水
                        库存状态: 充足
                        [证据2] [记录: store-service-001]
                        名称: 商品位置咨询
                        服务位置: 一层入口右侧服务台
                        说明: 机器人无法确认实时库存时，应引导顾客向服务台核实，不得承诺预留商品。
                        """))));

        assertTrue(result.content().contains("服务台"));
        assertTrue(result.content().contains("不得承诺预留商品"));
        assertFalse(result.content().contains("库存状态为充足"));
    }

    @Test
    void 应按证据字段原始顺序生成通用摘要() {
        var deployment = new ChatDeploymentRef(
                320000000000000001L,
                2);
        var model = new DeterministicChatModel(deployment);

        var result = model.chat(new ChatRequest(
                deployment,
                List.of(new ChatMessage(ChatRole.USER, """
                        问题：请概括这条授权信息。

                        授权证据：
                        [证据1] [记录: campus-notice-001]
                        名称: 暑期服务安排
                        生效日期: 2026-07-01
                        发布部门: 校务服务中心
                        """))));

        assertEquals(
                "名称：暑期服务安排，生效日期：2026-07-01，发布部门：校务服务中心。",
                result.content());
    }
}

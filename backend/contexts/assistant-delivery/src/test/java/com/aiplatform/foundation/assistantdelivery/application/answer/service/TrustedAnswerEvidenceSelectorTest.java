package com.aiplatform.foundation.assistantdelivery.application.answer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.IndexCandidate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrustedAnswerEvidenceSelectorTest {

    @Test
    void 应扩大候选池并把字段型比较所需记录排入最终证据() {
        var candidates = List.of(
                candidate(1, 0.22, "销量口径：最近 30 个自然日已完成订单件数"),
                candidate(2, 0.09, "浏览器验收商品，最近 30 日销量：88 件"),
                candidate(3, 0.06, "原味燕麦脆片，最近 30 日销量：173 袋"),
                candidate(4, 0.04, "商品位置咨询，机器人应引导顾客前往服务台"),
                candidate(5, -0.01, "青柚气泡水，最近 30 日销量：428 瓶"),
                candidate(6, -0.02, "便携保温杯，最近 30 日销量：64 个"));

        var selected = TrustedAnswerEvidenceSelector.select(
                "最近 30 天卖得最多的是哪件商品？", candidates, 5, 0);

        assertEquals(20, TrustedAnswerEvidenceSelector.candidateLimit(5));
        assertEquals(5, selected.size());
        assertTrue(selected.stream().anyMatch(value -> value.content().contains("青柚气泡水")));
        assertTrue(selected.stream().anyMatch(value -> value.content().contains("原味燕麦脆片")));
        assertTrue(selected.stream().anyMatch(value -> value.content().contains("便携保温杯")));
        assertTrue(selected.stream().noneMatch(value -> value.content().contains("服务台")));
    }

    @Test
    void 字面证据可以纠正向量排序且严格阈值保持拒答() {
        var relevant = candidate(1, 0.1, "青柚气泡水位于一层 A 区");
        var unrelated = candidate(2, 0.9, "中心图书馆周末开放时间");

        var selected = TrustedAnswerEvidenceSelector.select(
                "青柚气泡水位于哪里", List.of(unrelated, relevant), 1, 0.5);

        assertEquals(List.of(relevant.content()), selected.stream().map(IndexCandidate::content).toList());
        assertTrue(TrustedAnswerEvidenceSelector.select(
                "青柚气泡水位于哪里", List.of(unrelated, relevant), 1, 1.0).isEmpty());
        assertEquals(100, TrustedAnswerEvidenceSelector.candidateLimit(30));
    }

    private static IndexCandidate candidate(int suffix, double score, String content) {
        return new IndexCandidate(
                220000000000000000L + suffix,
                1,
                Integer.toHexString(suffix).repeat(64),
                suffix,
                content,
                score);
    }
}

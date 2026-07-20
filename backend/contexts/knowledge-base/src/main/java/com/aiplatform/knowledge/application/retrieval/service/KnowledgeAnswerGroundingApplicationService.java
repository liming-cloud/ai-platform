package com.aiplatform.knowledge.application.retrieval.service;

import com.aiplatform.knowledge.application.retrieval.result.RetrievedKnowledgeChunk;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 知识答案落地服务，负责在应用层校验模型回答是否严格锚定检索证据，
 * 并在回答失真时回退为可审核的抽取式答案。
 *
 * <p>这里不负责调用模型，也不负责领域策略判定，只负责“模型输出是否可对外返回”这条应用层规则，
 * 以避免应用服务继续膨胀，同时不把展示导向的回答拼装逻辑下沉到领域层。</p>
 */
@Service
public class KnowledgeAnswerGroundingApplicationService {
    private static final Pattern PRECISE_FACT_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?\\s*(?:个工作日|工作日|元|天|小时|分钟|个月|年|%)");

    public String stabilize(String query, List<RetrievedKnowledgeChunk> chunks, String generatedAnswer) {
        String normalizedAnswer = requireText(generatedAnswer, "模型回答");
        return isGroundedAnswer(chunks, normalizedAnswer) ? normalizedAnswer : buildExtractiveAnswer(query, chunks);
    }

    private boolean isGroundedAnswer(List<RetrievedKnowledgeChunk> chunks, String answer) {
        List<String> preciseFacts = extractPreciseFacts(chunks);
        if (preciseFacts.isEmpty()) {
            return true;
        }
        return preciseFacts.stream().allMatch(answer::contains);
    }

    private List<String> extractPreciseFacts(List<RetrievedKnowledgeChunk> chunks) {
        LinkedHashSet<String> facts = new LinkedHashSet<>();
        for (RetrievedKnowledgeChunk chunk : chunks) {
            var matcher = PRECISE_FACT_PATTERN.matcher(chunk.content());
            while (matcher.find()) {
                facts.add(matcher.group().trim());
            }
        }
        return List.copyOf(facts);
    }

    private String buildExtractiveAnswer(String query, List<RetrievedKnowledgeChunk> chunks) {
        List<String> conclusionSentences = new ArrayList<>();
        List<String> evidenceBlocks = new ArrayList<>();
        int evidenceIndex = 1;
        for (RetrievedKnowledgeChunk chunk : chunks) {
            List<String> selectedSentences = selectEvidenceSentences(query, chunk.content());
            if (selectedSentences.isEmpty()) {
                continue;
            }
            conclusionSentences.addAll(selectedSentences);
            evidenceBlocks.add("[E" + evidenceIndex++ + "] " + chunk.titlePath() + "\n原文：" + String.join(" ", selectedSentences));
            if (conclusionSentences.size() >= 3) {
                break;
            }
        }
        String conclusion = conclusionSentences.isEmpty() ? "根据当前证据无法确定" : String.join(" ", new LinkedHashSet<>(conclusionSentences));
        String evidence = evidenceBlocks.isEmpty() ? "[E1] 未提取到可引用原文" : String.join("\n", evidenceBlocks);
        return "结论：" + conclusion + "\n\n证据出处：\n" + evidence;
    }

    private List<String> selectEvidenceSentences(String query, String content) {
        List<String> sentences = splitEvidenceSentences(content);
        List<String> precise = sentences.stream().filter(sentence -> PRECISE_FACT_PATTERN.matcher(sentence).find()).limit(3).toList();
        if (!precise.isEmpty()) {
            return precise;
        }
        String normalizedQuery = query.replaceAll("\\s+", "");
        List<String> relevant = sentences.stream()
                .filter(sentence -> sentence.replaceAll("\\s+", "").contains(normalizedQuery)
                        || normalizedQuery.contains(sentence.replaceAll("\\s+", "")))
                .limit(3)
                .toList();
        if (!relevant.isEmpty()) {
            return relevant;
        }
        return sentences.stream().limit(2).toList();
    }

    private List<String> splitEvidenceSentences(String content) {
        String normalized = content.replaceAll("(?m)^#+\\s*", "").trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(normalized.split("(?<=[。！？；])\\s*|\\n+"))
                .map(String::trim)
                .filter(sentence -> !sentence.isBlank())
                .toList();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}

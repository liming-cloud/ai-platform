package com.aiplatform.foundation.provider.deterministic;

import com.aiplatform.foundation.modelruntime.api.invocation.ChatModelPort;
import com.aiplatform.foundation.modelruntime.api.model.chat.ChatChunk;
import com.aiplatform.foundation.modelruntime.api.model.chat.ChatDeploymentRef;
import com.aiplatform.foundation.modelruntime.api.model.chat.ChatRequest;
import com.aiplatform.foundation.modelruntime.api.model.chat.ChatResult;
import com.aiplatform.foundation.modelruntime.api.model.chat.ChatRole;
import com.aiplatform.foundation.modelruntime.api.model.common.ModelUsage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * 用于合同测试和演示自检的确定性对话模型。
 *
 * <p>该模型只解析平台生成的受控证据提示词，用于离线演示，不代表真实模型生成质量。</p>
 */
public final class DeterministicChatModel implements ChatModelPort {

    private static final String QUESTION_PREFIX = "问题：";
    private static final String EVIDENCE_PREFIX = "\n\n授权证据：";
    private static final Pattern EVIDENCE_MARKER =
            Pattern.compile("(?m)^\\[证据\\d+\\]\\s*");
    private static final Pattern RECORD_BOUNDARY =
            Pattern.compile("(?m)(?=\\[记录:)");

    private final ChatDeploymentRef deployment;

    public DeterministicChatModel(ChatDeploymentRef deployment) {
        this.deployment = Objects.requireNonNull(deployment, "确定性对话部署不能为空");
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        requireDeployment(request);
        return new ChatResult(
                deployment,
                answer(request),
                "STOP",
                ModelUsage.UNKNOWN);
    }

    @Override
    public Flow.Publisher<ChatChunk> stream(ChatRequest request) {
        requireDeployment(request);
        var answer = answer(request);
        return subscriber -> {
            Objects.requireNonNull(subscriber, "对话流订阅者不能为空");
            subscriber.onSubscribe(new Flow.Subscription() {
                private final AtomicBoolean completed = new AtomicBoolean();

                @Override
                public void request(long count) {
                    if (count < 1) {
                        subscriber.onError(new IllegalArgumentException("订阅请求数量必须大于零"));
                        return;
                    }
                    if (completed.compareAndSet(false, true)) {
                        subscriber.onNext(new ChatChunk(
                                deployment,
                                1,
                                answer,
                                false,
                                null));
                        subscriber.onNext(new ChatChunk(
                                deployment,
                                2,
                                "",
                                true,
                                ModelUsage.UNKNOWN));
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                    completed.set(true);
                }
            });
        };
    }

    private void requireDeployment(ChatRequest request) {
        Objects.requireNonNull(request, "对话请求不能为空");
        if (!deployment.equals(request.deployment())) {
            throw new IllegalArgumentException("确定性对话部署不匹配");
        }
    }

    private static String answer(ChatRequest request) {
        return request.messages().stream()
                .filter(message -> message.role() == ChatRole.USER)
                .reduce((left, right) -> right)
                .map(message -> answer(message.content()))
                .orElse("演示模型未收到用户消息。");
    }

    private static String answer(String prompt) {
        var separator = prompt.indexOf(EVIDENCE_PREFIX);
        if (!prompt.startsWith(QUESTION_PREFIX) || separator < 0) {
            return "演示模型未收到可解析的授权证据。";
        }
        var question = prompt.substring(QUESTION_PREFIX.length(), separator).trim();
        var records = parseRecords(prompt.substring(separator + EVIDENCE_PREFIX.length()));
        if (question.isBlank() || records.isEmpty()) {
            return "演示模型未收到可解析的授权证据。";
        }
        if (containsAny(question, "最多", "最高", "最大")) {
            var comparison = maximumSales(records);
            if (comparison.isPresent()) {
                return comparison.get();
            }
        }
        var record = records.stream()
                .max((left, right) -> Integer.compare(
                        relevance(left, question),
                        relevance(right, question)))
                .orElseThrow();
        return focusedAnswer(question, record);
    }

    private static List<EvidenceRecord> parseRecords(String evidence) {
        var withoutMarkers = EVIDENCE_MARKER.matcher(evidence).replaceAll("");
        var records = new ArrayList<EvidenceRecord>();
        for (var block : RECORD_BOUNDARY.split(withoutMarkers)) {
            if (!block.isBlank()) {
                var record = EvidenceRecord.parse(block);
                if (!record.fields().isEmpty()) {
                    records.add(record);
                }
            }
        }
        return List.copyOf(records);
    }

    private static Optional<String> maximumSales(List<EvidenceRecord> records) {
        EvidenceRecord maximum = null;
        var maximumQuantity = Long.MIN_VALUE;
        for (var record : records) {
            var quantity = leadingNumber(record.value("最近 30 日销量"));
            if (quantity > maximumQuantity && !record.value("名称").isBlank()) {
                maximum = record;
                maximumQuantity = quantity;
            }
        }
        if (maximum == null) {
            return Optional.empty();
        }
        return Optional.of(maximum.value("名称")
                + "最近 30 日销量最高，为 "
                + maximum.value("最近 30 日销量")
                + "。");
    }

    private static long leadingNumber(String value) {
        var digits = new StringBuilder();
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (Character.isDigit(character)) {
                digits.append(character);
            } else if (!digits.isEmpty()) {
                break;
            }
        }
        return digits.isEmpty() ? Long.MIN_VALUE : Long.parseLong(digits.toString());
    }

    private static int relevance(EvidenceRecord record, String question) {
        var score = question.contains(record.value("名称"))
                && !record.value("名称").isBlank()
                ? 100
                : 0;
        for (var index = 0; index + 1 < question.length(); index++) {
            var pair = question.substring(index, index + 2);
            if (record.raw().contains(pair)) {
                score++;
            }
        }
        if (question.contains("周末") && record.fields().containsKey("周六至周日")) {
            score += 20;
        }
        if (containsAny(question, "多少钱", "价格", "单价")
                && record.fields().containsKey("含税单价")) {
            score += 20;
        }
        if (containsAny(question, "还有货", "有货")
                && record.fields().containsKey("库存状态")) {
            score += 20;
        }
        if (containsAny(question, "怎么办", "不能", "无法")
                && record.fields().containsKey("说明")) {
            score += 30;
        }
        return score;
    }

    private static String focusedAnswer(String question, EvidenceRecord record) {
        var name = record.value("名称");
        if (containsAny(question, "多少钱", "价格", "单价")
                && containsAny(question, "哪里", "在哪", "位置")
                && record.hasValues("位置", "含税单价")) {
            return name
                    + "位于"
                    + record.value("位置")
                    + "，含税单价为"
                    + record.value("含税单价")
                    + "。";
        }
        if (containsAny(question, "还有货", "有货", "库存")
                && record.hasValues("库存状态")) {
            return name + "的库存状态为" + record.value("库存状态") + "。";
        }
        if (question.contains("周末") && record.hasValues("周六至周日")) {
            return name + "周末开放时间为" + record.value("周六至周日") + "。";
        }
        if (question.contains("借")
                && record.hasValues("借阅上限", "单次借期")) {
            return name
                    + "的借阅上限为"
                    + record.value("借阅上限")
                    + "，单次借期为"
                    + record.value("单次借期")
                    + "。";
        }
        if (containsAny(question, "哪里", "在哪", "补办")
                && record.hasValues("地点")) {
            var answer = name + "位于" + record.value("地点");
            if (record.hasValues("服务时间")) {
                answer += "，服务时间为" + record.value("服务时间");
            }
            return answer + "。";
        }
        if (containsAny(question, "签到", "怎么办", "如何", "不能", "无法")
                && record.hasValues("说明")) {
            return record.value("说明");
        }
        if (record.hasValues("说明")) {
            return name.isBlank()
                    ? record.value("说明")
                    : name + "：" + record.value("说明");
        }
        return record.summary();
    }

    private static boolean containsAny(String value, String... candidates) {
        for (var candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private record EvidenceRecord(String raw, Map<String, String> fields) {

        private EvidenceRecord {
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }

        private static EvidenceRecord parse(String raw) {
            var fields = new LinkedHashMap<String, String>();
            for (var line : raw.lines().toList()) {
                var separator = line.indexOf(':');
                if (separator < 0) {
                    separator = line.indexOf('：');
                }
                if (separator > 0 && separator + 1 < line.length()) {
                    var field = line.substring(0, separator).trim();
                    if (!field.startsWith("[记录")) {
                        fields.putIfAbsent(
                                field,
                                line.substring(separator + 1).trim());
                    }
                }
            }
            return new EvidenceRecord(raw, fields);
        }

        private String value(String field) {
            return fields.getOrDefault(field, "");
        }

        private boolean hasValues(String... names) {
            for (var name : names) {
                if (value(name).isBlank()) {
                    return false;
                }
            }
            return true;
        }

        private String summary() {
            return fields.entrySet().stream()
                    .filter(entry -> !entry.getValue().isBlank())
                    .limit(3)
                    .map(entry -> entry.getKey() + "：" + entry.getValue())
                    .reduce((left, right) -> left + "，" + right)
                    .orElse("演示模型未收到可解析的授权证据。")
                    + "。";
        }
    }
}

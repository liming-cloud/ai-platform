package com.aiplatform.knowledge.application.result;

import com.aiplatform.knowledge.domain.DocumentIngestJobSnapshot;
import com.aiplatform.knowledge.domain.DocumentIngestStatus;
import java.util.regex.Pattern;

/**
 * 浏览器可见的作业诊断策略。持久化原文只用于受控运维，不直接穿透到产品界面。
 */
final class KnowledgeDocumentJobDisclosurePolicy {
    static final String WITHHELD_SUMMARY = "任务失败，详细信息仅在受控日志中可见";
    private static final int MAX_SUMMARY_CODE_POINTS = 240;
    private static final Pattern SAFE_FAILURE_CODE = Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,63}");
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(api[-_ ]?key|token|secret|password|credential|authorization)\\s*[:=]\\s*\\S+"
    );
    private static final Pattern AUTHENTICATION_VALUE = Pattern.compile(
            "(?i)\\b(?:bearer|basic)\\s+[A-Za-z0-9+/=_\\-.]+"
    );
    private static final Pattern KNOWN_TOKEN = Pattern.compile(
            "(?i)\\b(?:sk|rk|pk|ghp|github_pat|xox[baprs])-?[A-Za-z0-9_-]{8,}"
    );
    private static final Pattern JWT = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)?\\b"
    );
    private static final Pattern PRIVATE_KEY = Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----");
    private static final Pattern URI_USER_INFO = Pattern.compile("(?i)://[^\\s/@:]+:[^\\s/@]+@");

    private KnowledgeDocumentJobDisclosurePolicy() {
    }

    static String failureCode(DocumentIngestJobSnapshot snapshot) {
        if (!hasFailure(snapshot)) {
            return null;
        }
        String value = snapshot.failureCode();
        return SAFE_FAILURE_CODE.matcher(value).matches() ? value : "AIP-KB-JOB-FAILED";
    }

    static String failureSummary(DocumentIngestJobSnapshot snapshot) {
        if (!hasFailure(snapshot)) {
            return null;
        }
        String normalized = snapshot.failureSummary().replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (normalized.isEmpty()
                || SENSITIVE_ASSIGNMENT.matcher(normalized).find()
                || AUTHENTICATION_VALUE.matcher(normalized).find()
                || KNOWN_TOKEN.matcher(normalized).find()
                || JWT.matcher(normalized).find()
                || PRIVATE_KEY.matcher(normalized).find()
                || URI_USER_INFO.matcher(normalized).find()) {
            return WITHHELD_SUMMARY;
        }
        return truncateCodePoints(normalized, MAX_SUMMARY_CODE_POINTS);
    }

    static String traceId(DocumentIngestJobSnapshot snapshot) {
        String value = snapshot.traceId();
        return SAFE_TRACE_ID.matcher(value).matches() ? value : null;
    }

    private static boolean hasFailure(DocumentIngestJobSnapshot snapshot) {
        return snapshot.status() == DocumentIngestStatus.FAILED
                || snapshot.status() == DocumentIngestStatus.RETRY_WAIT;
    }

    private static String truncateCodePoints(String value, int maxCodePoints) {
        int count = value.codePointCount(0, value.length());
        return count <= maxCodePoints ? value : value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }
}

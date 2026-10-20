package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.HexFormat;
import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * S2 单个精确目标的离线编排内核；包内可见，不注册 Bean、不访问凭据或仓储。
 * 生产协议和真实提交合同闭合前，不能由此生成 VERIFIED、发布或授权事实。
 */
final class PlatformCapabilityVerificationSession {
    static final Duration TIME_LIMIT = Duration.ofSeconds(30);
    static final BigDecimal FEE_LIMIT = new BigDecimal("0.03");
    private static final SecureRandom NONCES = new SecureRandom();
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final AtomicBoolean used = new AtomicBoolean();

    PlatformCapabilityVerificationSession(Clock clock, LongSupplier nanoTime) {
        this.clock = Objects.requireNonNull(clock, "报价时钟不能为空");
        this.nanoTime = Objects.requireNonNull(nanoTime, "单调时钟不能为空");
    }

    Summary run(Target target, Tariff tariff, BigDecimal feeLimit, BooleanSupplier cancelled, SampleCall call) {
        Objects.requireNonNull(target, "精确验证目标不能为空");
        Objects.requireNonNull(cancelled, "取消信号不能为空");
        Objects.requireNonNull(call, "离线协议调用方不能为空");
        var startedAt = nanoTime.getAsLong();
        var targetFingerprint = target.fingerprint();
        var passed = new ArrayList<String>();
        // 仅用于防止结果跨轮复用，不生成或替换业务实体标识。
        var nonce = new byte[32];
        NONCES.nextBytes(nonce);
        var plan = PlatformCapabilitySafetySuite.digest(targetFingerprint, PlatformCapabilitySafetySuite.VERSION,
                Objects.toString(tariff, ""), Objects.toString(feeLimit, ""), HexFormat.of().formatHex(nonce));
        if (!used.compareAndSet(false, true))
            return summary(Outcome.ALREADY_RUN, targetFingerprint, plan, BigDecimal.ZERO, 0, passed);
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean())
            return summary(Outcome.CANCELLED, targetFingerprint, plan, BigDecimal.ZERO, 0, passed);
        if (!pricingAvailable(target, tariff))
            return summary(Outcome.PRICING_UNAVAILABLE, targetFingerprint, plan, BigDecimal.ZERO, 0, passed);
        var samples = PlatformCapabilitySafetySuite.samples(target.type());
        // 整轮先预留，不能发出第一条后再发现其余样本无预算；金额使用十进制并向上取整。
        var reserved = tariff.maximumCost(target.maxOutputTokens()).multiply(BigDecimal.valueOf(samples.size()));
        if (feeLimit == null || feeLimit.signum() <= 0 || feeLimit.compareTo(FEE_LIMIT) > 0
                || reserved.compareTo(feeLimit) > 0)
            return summary(Outcome.BUDGET_EXCEEDED, targetFingerprint, plan, BigDecimal.ZERO, 0, passed);

        var attempted = 0;
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        Future<PlatformCapabilitySafetySuite.Reply> inFlight = null;
        try {
            for (var sample : samples) {
                var stopped = stopped(target, tariff, cancelled, startedAt);
                if (stopped != null) return summary(stopped, targetFingerprint, plan, reserved, attempted, passed);
                var timeout = remaining(startedAt);
                if (timeout <= 0)
                    return summary(Outcome.DEADLINE_EXCEEDED, targetFingerprint, plan, reserved, attempted, passed);
                var request = new Request(PlatformCapabilitySafetySuite.digest(plan, sample.fingerprint()), target,
                        sample, Duration.ofNanos(timeout), target.maxOutputTokens(), tariff.inputUnitsUpperBound(), tariff.inputTokensUpperBound());
                attempted++;
                inFlight = executor.submit(() -> {
                    // 调度等待也计入总预算；实际调用前再检查，不能沿用排队前的剩余时间。
                    if (stopped(target, tariff, cancelled, startedAt) != null) return null;
                    var dispatchTimeout = remaining(startedAt);
                    if (dispatchTimeout <= 0) return null;
                    return call.execute(new Request(request.binding(), target, sample,
                            Duration.ofNanos(dispatchTimeout), request.maxOutputTokens(), request.inputUnitsUpperBound(), request.inputTokensUpperBound()));
                });
                PlatformCapabilitySafetySuite.Reply reply;
                while (true) {
                    stopped = stopped(target, tariff, cancelled, startedAt);
                    if (stopped != null) return summary(stopped, targetFingerprint, plan, reserved, attempted, passed);
                    try {
                        reply = inFlight.get(Math.min(remaining(startedAt), TimeUnit.MILLISECONDS.toNanos(25)), TimeUnit.NANOSECONDS);
                        break;
                    } catch (TimeoutException exception) {
                        // 轮询仅等待同一个请求，绝不重试外呼；取消与总截止时间最多隔一个轮询周期复核。
                    }
                }
                stopped = stopped(target, tariff, cancelled, startedAt);
                if (stopped != null) return summary(stopped, targetFingerprint, plan, reserved, attempted, passed);
                if (reply == null || !request.binding().equals(reply.binding()) || reply.type() != target.type())
                    return summary(Outcome.INVALID_RESPONSE, targetFingerprint, plan, reserved, attempted, passed);
                if (!usageWithinReservation(target, tariff, reply.usage()))
                    return summary(Outcome.USAGE_EXCEEDED, targetFingerprint, plan, reserved, attempted, passed);
                if (!PlatformCapabilitySafetySuite.accepts(target.type(), target.embeddingDimension(), sample, reply))
                    return summary(Outcome.INVALID_RESPONSE, targetFingerprint, plan, reserved, attempted, passed);
                stopped = stopped(target, tariff, cancelled, startedAt);
                if (stopped != null) return summary(stopped, targetFingerprint, plan, reserved, attempted, passed);
                passed.add(sample.fingerprint());
            }
            return summary(Outcome.PASSED, targetFingerprint, plan, reserved, attempted, passed);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return summary(Outcome.CANCELLED, targetFingerprint, plan, reserved, attempted, passed);
        } catch (ExecutionException exception) {
            return summary(Outcome.CALL_FAILED, targetFingerprint, plan, reserved, attempted, passed);
        } finally {
            if (inFlight != null) inFlight.cancel(true);
            // 不使用会等待任务完成的 close；即使测试调用方忽略中断，迟到结果也无写入出口。
            executor.shutdownNow();
        }
    }

    private Outcome stopped(Target target, Tariff tariff, BooleanSupplier cancelled, long startedAt) {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) return Outcome.CANCELLED;
        if (remaining(startedAt) <= 0) return Outcome.DEADLINE_EXCEEDED;
        return pricingAvailable(target, tariff) ? null : Outcome.PRICING_UNAVAILABLE;
    }

    private long remaining(long startedAt) {
        var elapsed = nanoTime.getAsLong() - startedAt;
        return elapsed < 0 || elapsed >= TIME_LIMIT.toNanos() ? 0 : TIME_LIMIT.toNanos() - elapsed;
    }

    private boolean pricingAvailable(Target target, Tariff tariff) {
        var now = clock.instant();
        return tariff != null && target.fingerprint().equals(tariff.targetFingerprint())
                && isDigest(tariff.evidenceDigest()) && "CNY".equals(tariff.currency()) && tariff.unit() != null
                && tariff.observedAt() != null && !tariff.observedAt().isAfter(now)
                && tariff.expiresAt() != null && tariff.expiresAt().isAfter(now)
                && validPrice(tariff.inputPrice()) && validPrice(tariff.outputPrice())
                && tariff.inputUnitsUpperBound() > 0
                && tariff.inputTokensUpperBound() > 0
                && tariff.inputTokensUpperBound() <= (long) target.contextWindow() - target.maxOutputTokens()
                && (tariff.unit() == BillingUnit.PER_MILLION_TOKENS
                    ? tariff.inputUnitsUpperBound() == tariff.inputTokensUpperBound()
                    : tariff.inputUnitsUpperBound() == 1 && tariff.outputPrice().signum() == 0)
                && (target.type() == ModelType.CHAT || tariff.outputPrice().signum() == 0);
    }

    private static boolean validPrice(BigDecimal price) {
        return price != null && price.signum() >= 0 && price.precision() <= 18 && price.scale() >= 0 && price.scale() <= 9;
    }

    private static boolean usageWithinReservation(Target target, Tariff tariff, PlatformCapabilitySafetySuite.Usage usage) {
        return usage != null && usage.inputUnits() > 0 && usage.inputUnits() <= tariff.inputUnitsUpperBound()
                && usage.inputTokens() > 0 && usage.inputTokens() <= tariff.inputTokensUpperBound()
                && (tariff.unit() != BillingUnit.PER_MILLION_TOKENS || usage.inputUnits() == usage.inputTokens())
                && usage.outputTokens() >= 0 && usage.outputTokens() <= target.maxOutputTokens()
                && (target.type() != ModelType.CHAT || usage.outputTokens() > 0);
    }

    private static Summary summary(Outcome outcome, String target, String plan, BigDecimal fee, int attempted, List<String> passed) {
        return new Summary(outcome, target, plan, PlatformCapabilitySafetySuite.VERSION, fee, attempted, passed);
    }

    private static boolean isDigest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }

    /** 精确修订和输入摘要参与绑定；没有秘密、Endpoint 或租户权限。 */
    record Target(long deploymentId, long deploymentRevision, long connectionId, long connectionRevision,
                  String modelCode, ModelType type, String residency, String inputsDigest,
                  int contextWindow, int outputLimit, int embeddingDimension, int rerankCandidateLimit) {
        Target {
            Objects.requireNonNull(type, "能力类型不能为空");
            if (deploymentId <= 0 || connectionId <= 0 || deploymentRevision < 0 || connectionRevision < 0
                    || modelCode == null || modelCode.isBlank() || modelCode.length() > 200
                    || residency == null || residency.isBlank() || residency.length() > 80
                    || modelCode.chars().anyMatch(Character::isISOControl) || residency.chars().anyMatch(Character::isISOControl)
                    || !isDigest(inputsDigest) || contextWindow <= 0
                    || (type == ModelType.CHAT ? outputLimit <= 0 || outputLimit > contextWindow : outputLimit != 0)
                    || (type == ModelType.EMBEDDING ? embeddingDimension <= 0 : embeddingDimension != 0)
                    || (type == ModelType.RERANK ? rerankCandidateLimit < 3 : rerankCandidateLimit != 0))
                throw new IllegalArgumentException("验证目标必须绑定精确修订及与三样本匹配的声明");
        }
        int maxOutputTokens() { return type == ModelType.CHAT ? Math.min(64, outputLimit) : 0; }
        String fingerprint() {
            return PlatformCapabilitySafetySuite.digest(Long.toString(deploymentId), Long.toString(deploymentRevision),
                    Long.toString(connectionId), Long.toString(connectionRevision), modelCode, type.name(), residency,
                    inputsDigest, Integer.toString(contextWindow), Integer.toString(outputLimit),
                    Integer.toString(embeddingDimension), Integer.toString(rerankCandidateLimit));
        }
    }

    enum BillingUnit { PER_MILLION_TOKENS, PER_REQUEST }

    /**
     * 报价证据和输入用量上界由执行材料提供，本类只检查绑定与时效。
     * 生产调用方仍须独立核验来源真实性，不能仅凭摘要格式有效宣称已取得账户报价。
     */
    record Tariff(String targetFingerprint, String evidenceDigest, Instant observedAt, Instant expiresAt,
                  String currency, BillingUnit unit, BigDecimal inputPrice, BigDecimal outputPrice,
                  long inputUnitsUpperBound, long inputTokensUpperBound) {
        BigDecimal maximumCost(int outputTokens) {
            if (unit == BillingUnit.PER_REQUEST) return inputPrice.setScale(9, RoundingMode.CEILING);
            return inputPrice.multiply(BigDecimal.valueOf(inputUnitsUpperBound))
                    .add(outputPrice.multiply(BigDecimal.valueOf(outputTokens)))
                    .divide(BigDecimal.valueOf(1_000_000), 9, RoundingMode.CEILING);
        }
    }

    record Request(String binding, Target target, PlatformCapabilitySafetySuite.Sample sample,
                   Duration timeout, int maxOutputTokens, long inputUnitsUpperBound, long inputTokensUpperBound) {}

    /** 当前仅供离线合同调用方提供可控返回；不注册厂商适配或授权外呼。 */
    @FunctionalInterface
    interface SampleCall { PlatformCapabilitySafetySuite.Reply execute(Request request) throws Exception; }

    enum Outcome { PASSED, PRICING_UNAVAILABLE, BUDGET_EXCEEDED, INVALID_RESPONSE, USAGE_EXCEEDED,
        DEADLINE_EXCEEDED, CANCELLED, CALL_FAILED, ALREADY_RUN }

    /** 全轮费用始终保守预留，不用失败或取消后的未知用量自动退款；不是实扣账单。 */
    record Summary(Outcome outcome, String targetFingerprint, String planFingerprint, String sampleVersion,
                   BigDecimal reservedFee, int attemptedRequests, List<String> passedSampleDigests) {
        Summary { passedSampleDigests = List.copyOf(passedSampleDigests); }
    }
}

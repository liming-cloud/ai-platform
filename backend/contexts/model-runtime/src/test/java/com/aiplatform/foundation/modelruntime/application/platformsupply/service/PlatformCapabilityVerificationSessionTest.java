package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import static org.junit.jupiter.api.Assertions.*;
import static com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformCapabilitySafetySuite.*;
import static com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformCapabilityVerificationSession.*;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PlatformCapabilityVerificationSessionTest {
    private static final Instant NOW = Instant.parse("2026-10-20T06:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @ParameterizedTest
    @EnumSource(ModelType.class)
    void 每个能力按固定版本顺序执行三个样本且预留全轮费用(ModelType type) {
        var target = target(type);
        var seen = new ArrayList<Request>();
        var result = session().run(target, tariff(target), FEE_LIMIT, () -> false, request -> {
            seen.add(request);
            assertTrue(request.timeout().compareTo(TIME_LIMIT) <= 0);
            assertFalse(request.timeout().isZero());
            assertEquals(type == ModelType.CHAT ? 64 : 0, request.maxOutputTokens());
            return valid(request);
        });
        assertEquals(Outcome.PASSED, result.outcome());
        assertEquals(3, result.attemptedRequests());
        assertEquals(samples(type).stream().map(Sample::id).toList(), seen.stream().map(r -> r.sample().id()).toList());
        assertEquals(3, seen.stream().map(Request::binding).distinct().count());
        assertEquals(samples(type).stream().map(Sample::fingerprint).toList(), result.passedSampleDigests());
        assertEquals(target.fingerprint(), result.targetFingerprint());
        assertEquals(VERSION, result.sampleVersion());
        assertTrue(result.reservedFee().signum() > 0);
        assertTrue(result.reservedFee().compareTo(FEE_LIMIT) <= 0);
        if (type == ModelType.CHAT) assertEquals(0, result.reservedFee().compareTo(new BigDecimal("0.012096")));
    }

    @Test
    void 首次外呼前能冻结整轮计划和预留且后续样本不能变更() {
        var target = target(ModelType.CHAT);
        var seen = new ArrayList<Request>();
        var result = session().run(target, tariff(target), FEE_LIMIT, () -> false, request -> {
            assertNotNull(request.planFingerprint(), "持久化预留不能等外呼结束才取得计划");
            assertNotNull(request.reservedFee(), "首次外呼前必须能持久化整轮费用上界");
            seen.add(request); return valid(request);
        });
        assertEquals(Outcome.PASSED, result.outcome());
        assertEquals(3, seen.size());
        assertTrue(seen.stream().allMatch(request -> request.planFingerprint().equals(result.planFingerprint())
                && request.reservedFee().compareTo(result.reservedFee()) == 0));
    }

    @Test
    void 缺失过期未来或不属于当前修订的报价一律在调用前拒绝() {
        var target = target(ModelType.CHAT);
        var good = tariff(target);
        var quotes = new ArrayList<Tariff>();
        quotes.add(null);
        quotes.add(new Tariff("b".repeat(64), good.evidenceDigest(), good.observedAt(), good.expiresAt(), "CNY",
                good.unit(), good.inputPrice(), good.outputPrice(), 1024, 1024));
        quotes.add(new Tariff(target.fingerprint(), "", good.observedAt(), good.expiresAt(), "CNY",
                good.unit(), good.inputPrice(), good.outputPrice(), 1024, 1024));
        quotes.add(new Tariff(target.fingerprint(), good.evidenceDigest(), NOW.minusSeconds(60), NOW, "CNY",
                good.unit(), good.inputPrice(), good.outputPrice(), 1024, 1024));
        quotes.add(new Tariff(target.fingerprint(), good.evidenceDigest(), NOW.plusSeconds(1), good.expiresAt(), "CNY",
                good.unit(), good.inputPrice(), good.outputPrice(), 1024, 1024));
        quotes.add(new Tariff(target.fingerprint(), good.evidenceDigest(), good.observedAt(), good.expiresAt(), "USD",
                good.unit(), good.inputPrice(), good.outputPrice(), 1024, 1024));
        quotes.add(new Tariff(target.fingerprint(), good.evidenceDigest(), good.observedAt(), good.expiresAt(), "CNY",
                good.unit(), good.inputPrice(), good.outputPrice(), 0, 1024));
        quotes.add(new Tariff(target.fingerprint(), good.evidenceDigest(), good.observedAt(), good.expiresAt(), "CNY",
                null, good.inputPrice(), good.outputPrice(), 1024, 1024));
        quotes.add(new Tariff(target.fingerprint(), good.evidenceDigest(), good.observedAt(), good.expiresAt(), "CNY",
                good.unit(), null, good.outputPrice(), 1024, 1024));
        quotes.add(new Tariff(target.fingerprint(), good.evidenceDigest(), good.observedAt(), good.expiresAt(), "CNY",
                good.unit(), good.inputPrice().negate(), good.outputPrice(), 1024, 1024));
        for (var quote : quotes) {
            var count = new AtomicInteger();
            var result = session().run(target, quote, FEE_LIMIT, () -> false, request -> {
                count.incrementAndGet(); return valid(request);
            });
            assertEquals(Outcome.PRICING_UNAVAILABLE, result.outcome());
            assertEquals(0, count.get());
        }
    }

    @Test
    void 全轮预算不足不能先发一条且不能提高已接受上限() {
        var target = target(ModelType.CHAT);
        for (var limit : List.of(new BigDecimal("0.01"), new BigDecimal("0.031"), BigDecimal.ZERO)) {
            var result = session().run(target, tariff(target), limit, () -> false, request -> {
                fail("预算不足不得调用协议"); return valid(request);
            });
            assertEquals(Outcome.BUDGET_EXCEEDED, result.outcome());
            assertEquals(0, result.attemptedRequests());
        }
    }

    @Test
    void 输入计量上界加生成预留不能超过声明的上下文() {
        var target = target(ModelType.CHAT);
        var good = tariff(target);
        var quote = new Tariff(target.fingerprint(), good.evidenceDigest(), good.observedAt(), good.expiresAt(),
                "CNY", good.unit(), new BigDecimal("0.1"), new BigDecimal("0.1"), target.contextWindow(), target.contextWindow());
        var result = session().run(target, quote, FEE_LIMIT, () -> false, request -> valid(request));
        assertEquals(Outcome.PRICING_UNAVAILABLE, result.outcome());
        assertEquals(0, result.attemptedRequests());
    }

    @Test
    void 按请求计费使用明确单位并验证十进制整轮边界() {
        var target = target(ModelType.RERANK);
        var good = tariff(target);
        for (var price : List.of(new BigDecimal("0.01"), new BigDecimal("0.010000001"))) {
            var quote = new Tariff(target.fingerprint(), good.evidenceDigest(), good.observedAt(), good.expiresAt(),
                    "CNY", BillingUnit.PER_REQUEST, price, BigDecimal.ZERO, 1, 1024);
            var result = session().run(target, quote, FEE_LIMIT, () -> false, request -> {
                var reply = valid(request);
                return new Reply(reply.binding(), reply.type(), null, reply.embeddings(), reply.ranks(), new Usage(1, 1, 0), false);
            });
            assertEquals(price.compareTo(new BigDecimal("0.01")) == 0 ? Outcome.PASSED : Outcome.BUDGET_EXCEEDED,
                    result.outcome());
            assertEquals(price.compareTo(new BigDecimal("0.01")) == 0 ? 3 : 0, result.attemptedRequests());
        }
    }

    @Test
    void 微小费用也向上预留不能四舍五入成免费() {
        var target = target(ModelType.EMBEDDING);
        var good = tariff(target);
        var quote = new Tariff(target.fingerprint(), good.evidenceDigest(), good.observedAt(), good.expiresAt(),
                "CNY", BillingUnit.PER_MILLION_TOKENS, new BigDecimal("0.000000001"), BigDecimal.ZERO, 1, 1);
        var result = session().run(target, quote, FEE_LIMIT, () -> false, request -> {
            var reply = valid(request);
            return new Reply(reply.binding(), reply.type(), null, reply.embeddings(), reply.ranks(), new Usage(1, 1, 0), false);
        });
        assertEquals(Outcome.PASSED, result.outcome());
        assertEquals(0, result.reservedFee().compareTo(new BigDecimal("0.000000003")));
    }

    @Test
    void 按请求计费不能代替独立输入Token上界证明() {
        var target = target(ModelType.RERANK);
        var good = tariff(target);
        for (var tokens : List.of(0L, 4097L)) {
            var quote = new Tariff(target.fingerprint(), good.evidenceDigest(), good.observedAt(), good.expiresAt(),
                    "CNY", BillingUnit.PER_REQUEST, new BigDecimal("0.01"), BigDecimal.ZERO, 1, tokens);
            var result = session().run(target, quote, FEE_LIMIT, () -> false, request -> {
                var reply = valid(request);
                return new Reply(reply.binding(), reply.type(), null, reply.embeddings(), reply.ranks(), new Usage(1, 1, 0), false);
            });
            assertEquals(Outcome.PRICING_UNAVAILABLE, result.outcome());
            assertEquals(0, result.attemptedRequests());
        }
    }

    @Test
    void 按请求计费的实际Token超出证据仍然不能通过() {
        var target = target(ModelType.RERANK);
        var good = tariff(target);
        var quote = new Tariff(target.fingerprint(), good.evidenceDigest(), good.observedAt(), good.expiresAt(),
                "CNY", BillingUnit.PER_REQUEST, new BigDecimal("0.01"), BigDecimal.ZERO, 1, 1024);
        var result = session().run(target, quote, FEE_LIMIT, () -> false, request -> {
            var reply = valid(request);
            return new Reply(reply.binding(), reply.type(), null, reply.embeddings(), reply.ranks(), new Usage(1, 1025, 0), false);
        });
        assertEquals(Outcome.USAGE_EXCEEDED, result.outcome());
        assertEquals(1, result.attemptedRequests());
        assertTrue(result.passedSampleDigests().isEmpty());
    }

    @Test
    void 报价不能跨连接部署输入或参数修订复用() {
        var original = target(ModelType.EMBEDDING);
        var variants = List.of(
                new Target(301, 1, 201, 2, original.modelCode(), original.type(), "cn", "a".repeat(64), 4096, 0, 1024, 0),
                new Target(301, 0, 201, 3, original.modelCode(), original.type(), "cn", "a".repeat(64), 4096, 0, 1024, 0),
                new Target(301, 0, 201, 2, original.modelCode(), original.type(), "cn", "b".repeat(64), 4096, 0, 1024, 0),
                new Target(301, 0, 201, 2, original.modelCode(), original.type(), "cn", "a".repeat(64), 4096, 0, 512, 0));
        for (var target : variants) {
            assertNotEquals(original.fingerprint(), target.fingerprint());
            var result = session().run(target, tariff(original), FEE_LIMIT, () -> false, request -> {
                fail("旧报价不能用于新目标"); return valid(request);
            });
            assertEquals(Outcome.PRICING_UNAVAILABLE, result.outcome());
            assertEquals(0, result.attemptedRequests());
        }
    }

    @Test
    void 调度前恰好达到截止时间时不能向调用方发请求() {
        var reads = new AtomicInteger();
        var cancelReads = new AtomicInteger();
        var calls = new AtomicInteger();
        var entered = new CountDownLatch(1);
        var target = target(ModelType.CHAT);
        var session = new PlatformCapabilityVerificationSession(CLOCK,
                () -> reads.incrementAndGet() < 3 ? 0 : TIME_LIMIT.toNanos());
        var result = session.run(target, tariff(target), FEE_LIMIT, () -> {
            if (cancelReads.incrementAndGet() == 3) {
                try { entered.await(1, TimeUnit.SECONDS); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            }
            return false;
        }, request -> { calls.incrementAndGet(); entered.countDown(); return valid(request); });
        assertEquals(Outcome.DEADLINE_EXCEEDED, result.outcome());
        assertEquals(0, calls.get());
    }

    @Test
    void 固定样本失败立即停止且不能重跑会话() {
        var target = target(ModelType.CHAT);
        var session = session();
        var result = session.run(target, tariff(target), FEE_LIMIT, () -> false, request ->
                new Reply(request.binding(), ModelType.CHAT, "不应输出的合成正文", List.of(), List.of(), new Usage(10, 10, 3), false));
        assertEquals(Outcome.INVALID_RESPONSE, result.outcome());
        assertEquals(1, result.attemptedRequests());
        assertEquals(0, result.passedSampleDigests().size());
        assertFalse(result.toString().contains("不应输出"));
        assertEquals(Outcome.ALREADY_RUN, session.run(target, tariff(target), FEE_LIMIT, () -> false, request -> {
            fail("不能重跑会话"); return valid(request);
        }).outcome());
    }

    @Test
    void 缺用量或超出输入输出预留不能用正确正文冒充成功() {
        var target = target(ModelType.CHAT);
        var usages = new ArrayList<Usage>();
        usages.add(null); usages.add(new Usage(1025, 1025, 1)); usages.add(new Usage(10, 10, 65));
        usages.add(new Usage(-1, -1, 1)); usages.add(new Usage(10, 10, 0));
        for (var usage : usages) {
            var result = session().run(target, tariff(target), FEE_LIMIT, () -> false, request ->
                    new Reply(request.binding(), ModelType.CHAT, "OK", List.of(), List.of(), usage, false));
            assertEquals(Outcome.USAGE_EXCEEDED, result.outcome());
            assertEquals(1, result.attemptedRequests());
        }
    }

    @Test
    void 同一目标的旧请求结果也不能跨样本或会话复用() {
        var target = target(ModelType.CHAT);
        var result = session().run(target, tariff(target), FEE_LIMIT, () -> false, request ->
                new Reply("旧请求摘要", ModelType.CHAT, "OK", List.of(), List.of(), new Usage(10, 10, 1), false));
        assertEquals(Outcome.INVALID_RESPONSE, result.outcome());
        assertEquals(1, result.attemptedRequests());
    }

    @Test
    void 相同目标和样本的新一轮使用独立绑定且拒绝前一轮的真实返回() {
        var target = target(ModelType.CHAT);
        var previous = new java.util.concurrent.atomic.AtomicReference<Reply>();
        var first = session().run(target, tariff(target), FEE_LIMIT, () -> false, request -> {
            var reply = valid(request);
            previous.compareAndSet(null, reply);
            return reply;
        });
        var second = session().run(target, tariff(target), FEE_LIMIT, () -> false, request -> previous.get());
        assertEquals(Outcome.PASSED, first.outcome());
        assertNotEquals(first.planFingerprint(), second.planFingerprint());
        assertEquals(Outcome.INVALID_RESPONSE, second.outcome());
        assertEquals(1, second.attemptedRequests());
        assertTrue(second.passedSampleDigests().isEmpty());
    }

    @Test
    void 单调总截止时间不会在第二个样本重置() {
        var nanos = new AtomicLong();
        var target = target(ModelType.CHAT);
        var session = new PlatformCapabilityVerificationSession(CLOCK, nanos::get);
        var result = session.run(target, tariff(target), FEE_LIMIT, () -> false, request -> {
            if (request.sample().id().equals("C01")) nanos.addAndGet(Duration.ofSeconds(20).toNanos());
            else {
                assertTrue(request.timeout().compareTo(Duration.ofSeconds(10)) <= 0);
                nanos.addAndGet(Duration.ofSeconds(11).toNanos());
            }
            return valid(request);
        });
        assertEquals(Outcome.DEADLINE_EXCEEDED, result.outcome());
        assertEquals(2, result.attemptedRequests());
        assertEquals(1, result.passedSampleDigests().size());
    }

    @Test
    void 阻塞调用超时取消后不等迟到结果也不继续调用() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            var nanos = new AtomicLong();
            var target = target(ModelType.CHAT);
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var interrupted = new CountDownLatch(1);
            try (var controller = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                controller.submit(() -> {
                    try {
                        assertTrue(started.await(1, TimeUnit.SECONDS));
                        nanos.set(TIME_LIMIT.toNanos());
                    } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                });
                var result = new PlatformCapabilityVerificationSession(CLOCK, nanos::get)
                        .run(target, tariff(target), FEE_LIMIT, () -> false, request -> {
                            started.countDown();
                            try { release.await(); }
                            catch (InterruptedException exception) {
                                interrupted.countDown(); release.await();
                            }
                            return valid(request);
                        });
                assertEquals(Outcome.DEADLINE_EXCEEDED, result.outcome());
                assertEquals(1, result.attemptedRequests());
                assertTrue(result.passedSampleDigests().isEmpty());
                assertTrue(interrupted.await(1, TimeUnit.SECONDS));
            } finally { release.countDown(); }
        });
    }

    @Test
    void 取消和调用异常均停止且不泄漏原始错误也不重试() {
        var target = target(ModelType.CHAT);
        var cancelled = session().run(target, tariff(target), FEE_LIMIT, () -> true, request -> {
            fail("取消后不能调用"); return valid(request);
        });
        assertEquals(Outcome.CANCELLED, cancelled.outcome());
        assertEquals(0, cancelled.attemptedRequests());
        var failed = session().run(target, tariff(target), FEE_LIMIT, () -> false, request -> {
            throw new IllegalStateException("合成厂商响应，不应写入摘要");
        });
        assertEquals(Outcome.CALL_FAILED, failed.outcome());
        assertEquals(1, failed.attemptedRequests());
        assertFalse(failed.toString().contains("合成厂商"));
        assertTrue(failed.reservedFee().signum() > 0);
    }

    @Test
    void 阻塞期间取消会中断当前任务并且不接纳迟到结果() throws Exception {
        var target = target(ModelType.CHAT);
        var cancelled = new AtomicBoolean();
        var entered = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var controller = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var future = controller.submit(() -> session().run(target, tariff(target), FEE_LIMIT, cancelled::get, request -> {
                entered.countDown();
                try { release.await(); }
                catch (InterruptedException exception) { interrupted.countDown(); release.await(); }
                return valid(request);
            }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            cancelled.set(true);
            var result = future.get(2, TimeUnit.SECONDS);
            assertEquals(Outcome.CANCELLED, result.outcome());
            assertEquals(1, result.attemptedRequests());
            assertTrue(result.passedSampleDigests().isEmpty());
            assertTrue(result.reservedFee().signum() > 0);
            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        } finally { release.countDown(); }
    }

    @Test
    void 调用线程已中断时保留中断标识且零请求() {
        var target = target(ModelType.CHAT);
        Thread.currentThread().interrupt();
        try {
            var result = session().run(target, tariff(target), FEE_LIMIT, () -> false, request -> {
                fail("中断后不能调用"); return valid(request);
            });
            assertEquals(Outcome.CANCELLED, result.outcome());
            assertEquals(0, result.attemptedRequests());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    static PlatformCapabilityVerificationSession session() {
        return new PlatformCapabilityVerificationSession(CLOCK, System::nanoTime);
    }

    static Target target(ModelType type) {
        return new Target(301, 0, 201, 2, "synthetic-" + type, type, "cn", "a".repeat(64),
                4096, type == ModelType.CHAT ? 64 : 0,
                type == ModelType.EMBEDDING ? 1024 : 0, type == ModelType.RERANK ? 3 : 0);
    }

    static Tariff tariff(Target target) {
        return new Tariff(target.fingerprint(), "c".repeat(64), NOW.minusSeconds(60), NOW.plusSeconds(3600),
                "CNY", BillingUnit.PER_MILLION_TOKENS, new BigDecimal("3"),
                target.type() == ModelType.CHAT ? new BigDecimal("15") : BigDecimal.ZERO, 1024, 1024);
    }

    static Reply valid(Request request) {
        var sample = request.sample();
        return switch (request.target().type()) {
            case CHAT -> new Reply(request.binding(), ModelType.CHAT, sample.expectedText(), List.of(), List.of(), new Usage(10, 10, 1), false);
            case EMBEDDING -> new Reply(request.binding(), ModelType.EMBEDDING, null,
                    List.of(new Embedding(0, java.util.Collections.nCopies(1024, 0.5))), List.of(), new Usage(10, 10, 0), false);
            case RERANK -> {
                var first = sample.expectedFirstIndex();
                var order = first == 0 ? List.of(new Ranked(0, 2), new Ranked(1, 1), new Ranked(2, -1))
                        : List.of(new Ranked(1, 2), new Ranked(0, 1), new Ranked(2, -1));
                yield new Reply(request.binding(), ModelType.RERANK, null, List.of(), order, new Usage(10, 10, 0), false);
            }
        };
    }
}

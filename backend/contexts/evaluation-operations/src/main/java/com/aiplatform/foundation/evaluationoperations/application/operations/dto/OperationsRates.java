package com.aiplatform.foundation.evaluationoperations.application.operations.dto;

/** 运营总览使用的稳定比率，取值范围为零到一。 */
public record OperationsRates(
        double answerRate,
        double refusalRate,
        double failureRate) {

    /**
     * 以受理查询数为唯一分母形成比率；没有受理查询时统一返回零。
     *
     * @throws IllegalArgumentException 任一计数为负数，或终态计数超过受理数
     */
    public static OperationsRates from(
            long accepted,
            long answered,
            long refused,
            long failed) {
        if (accepted < 0L || answered < 0L || refused < 0L || failed < 0L) {
            throw new IllegalArgumentException("运营查询计数不能为负数");
        }
        if (answered + refused + failed > accepted) {
            throw new IllegalArgumentException("查询终态计数不能超过受理数");
        }
        if (accepted == 0L) {
            return new OperationsRates(0.0d, 0.0d, 0.0d);
        }
        return new OperationsRates(
                ratio(answered, accepted),
                ratio(refused, accepted),
                ratio(failed, accepted));
    }

    private static double ratio(long numerator, long denominator) {
        return Math.round(((double) numerator / denominator) * 10_000.0d) / 10_000.0d;
    }
}

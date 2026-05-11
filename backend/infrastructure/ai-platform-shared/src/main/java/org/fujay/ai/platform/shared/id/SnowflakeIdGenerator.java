package org.fujay.ai.platform.shared.id;

public final class SnowflakeIdGenerator {

    private static final long EPOCH_MILLIS = 1_704_067_200_000L;
    private static final int NODE_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    private static final long MAX_NODE_ID = (1L << NODE_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final int TIMESTAMP_SHIFT = NODE_BITS + SEQUENCE_BITS;
    private static final int NODE_SHIFT = SEQUENCE_BITS;
    private static final SnowflakeIdGenerator DEFAULT = new SnowflakeIdGenerator(
            Long.getLong("ai.platform.node-id", 1L));

    private final long nodeId;
    private long lastTimestamp = -1L;
    private long sequence;

    public SnowflakeIdGenerator(long nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException("雪花节点编号必须在 0 到 " + MAX_NODE_ID + " 之间");
        }
        this.nodeId = nodeId;
    }

    public static long nextId() {
        return DEFAULT.next();
    }

    public synchronized long next() {
        long currentTimestamp = currentTimeMillis();
        if (currentTimestamp < lastTimestamp) {
            throw new IllegalStateException("系统时钟回拨，无法生成雪花ID");
        }
        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                currentTimestamp = waitNextMillis(currentTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = currentTimestamp;
        return ((currentTimestamp - EPOCH_MILLIS) << TIMESTAMP_SHIFT) | (nodeId << NODE_SHIFT) | sequence;
    }

    private long waitNextMillis(long currentTimestamp) {
        long timestamp = currentTimestamp;
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}

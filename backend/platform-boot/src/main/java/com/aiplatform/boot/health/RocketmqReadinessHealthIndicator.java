package com.aiplatform.boot.health;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.protocol.RequestCode;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 主动就绪探针。仅发送只读管理命令，不发送业务消息，也不暴露连接或错误详情。
 */
@Component("rocketmqHealthIndicator")
final class RocketmqReadinessHealthIndicator implements HealthIndicator {
    private static final int MAX_RESPONSE_FRAME_BYTES = 1_048_576;
    private final List<RocketmqReadinessEndpoint> nameServers;
    private final List<RocketmqReadinessEndpoint> brokers;
    private final Duration connectTimeout;
    private final BiPredicate<RocketmqReadinessEndpoint, Duration> nameServerProbe;
    private final BiPredicate<RocketmqReadinessEndpoint, Duration> brokerProbe;

    @Autowired
    RocketmqReadinessHealthIndicator(RocketmqReadinessProperties properties) {
        this(
                properties,
                (endpoint, timeout) -> isRocketmqResponsive(
                        endpoint,
                        timeout,
                        RequestCode.GET_NAMESRV_CONFIG
                ),
                (endpoint, timeout) -> isRocketmqResponsive(
                        endpoint,
                        timeout,
                        RequestCode.GET_BROKER_RUNTIME_INFO
                )
        );
    }

    RocketmqReadinessHealthIndicator(
            RocketmqReadinessProperties properties,
            BiPredicate<RocketmqReadinessEndpoint, Duration> endpointProbe
    ) {
        this(properties, endpointProbe, endpointProbe);
    }

    private RocketmqReadinessHealthIndicator(
            RocketmqReadinessProperties properties,
            BiPredicate<RocketmqReadinessEndpoint, Duration> nameServerProbe,
            BiPredicate<RocketmqReadinessEndpoint, Duration> brokerProbe
    ) {
        RocketmqReadinessProperties requiredProperties = Objects.requireNonNull(
                properties,
                "RocketMQ readiness 配置不能为空"
        );
        this.nameServers = immutableEndpoints(requiredProperties.getNameServers());
        this.brokers = immutableEndpoints(requiredProperties.getBrokers());
        this.connectTimeout = Objects.requireNonNull(
                requiredProperties.getConnectTimeout(),
                "RocketMQ readiness 连接超时不能为空"
        );
        this.nameServerProbe = Objects.requireNonNull(nameServerProbe, "RocketMQ name-server 探针不能为空");
        this.brokerProbe = Objects.requireNonNull(brokerProbe, "RocketMQ broker 探针不能为空");
    }

    @Override
    public Health health() {
        boolean ready = anyReachable(nameServers, nameServerProbe)
                && anyReachable(brokers, brokerProbe);
        return ready ? Health.up().build() : Health.down().build();
    }

    private boolean anyReachable(
            List<RocketmqReadinessEndpoint> endpoints,
            BiPredicate<RocketmqReadinessEndpoint, Duration> endpointProbe
    ) {
        for (RocketmqReadinessEndpoint endpoint : endpoints) {
            try {
                if (endpointProbe.test(endpoint, connectTimeout)) {
                    return true;
                }
            } catch (Exception ignored) {
                // 端点错误属于健康状态，不进入响应详情；继续尝试同组剩余端点。
            }
        }
        return false;
    }

    private static List<RocketmqReadinessEndpoint> immutableEndpoints(
            List<RocketmqReadinessEndpoint> endpoints
    ) {
        if (endpoints == null) {
            return List.of();
        }
        return endpoints.stream().map(RocketmqReadinessHealthIndicator::copyEndpoint).toList();
    }

    private static RocketmqReadinessEndpoint copyEndpoint(
            RocketmqReadinessEndpoint source
    ) {
        RocketmqReadinessEndpoint requiredSource = Objects.requireNonNull(
                source,
                "RocketMQ readiness endpoint 不能为空"
        );
        RocketmqReadinessEndpoint copy = new RocketmqReadinessEndpoint();
        copy.setHost(requiredSource.getHost());
        copy.setPort(requiredSource.getPort());
        return copy;
    }

    private static boolean isRocketmqResponsive(
            RocketmqReadinessEndpoint endpoint,
            Duration timeout,
            int requestCode
    ) {
        try (Socket socket = new Socket()) {
            int timeoutMillis = Math.toIntExact(timeout.toMillis());
            socket.connect(
                    new InetSocketAddress(endpoint.getHost(), endpoint.getPort()),
                    timeoutMillis
            );
            socket.setSoTimeout(timeoutMillis);
            RemotingCommand request = RemotingCommand.createRequestCommand(requestCode, null);
            writeCommand(socket, request);
            RemotingCommand response = readCommand(socket);
            return response.isResponseType()
                    && response.getOpaque() == request.getOpaque()
                    && response.getCode() == ResponseCode.SUCCESS;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void writeCommand(Socket socket, RemotingCommand command) throws IOException {
        ByteBuffer encoded = command.encode();
        byte[] frame = new byte[encoded.remaining()];
        encoded.get(frame);
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());
        output.write(frame);
        output.flush();
    }

    private static RemotingCommand readCommand(Socket socket) throws Exception {
        DataInputStream input = new DataInputStream(socket.getInputStream());
        int frameLength = input.readInt();
        if (frameLength < Integer.BYTES || frameLength > MAX_RESPONSE_FRAME_BYTES) {
            throw new IOException("RocketMQ readiness 响应帧长度非法");
        }
        byte[] frame = input.readNBytes(frameLength);
        if (frame.length != frameLength) {
            throw new IOException("RocketMQ readiness 响应帧不完整");
        }
        return RemotingCommand.decode(ByteBuffer.wrap(frame));
    }
}

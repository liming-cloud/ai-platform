package com.aiplatform.boot.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiPredicate;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.protocol.RequestCode;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class RocketmqReadinessHealthIndicatorTest {
    @Test
    void actualProbeUsesReadOnlyRocketmqCommandsWithoutBusinessPayload() throws Exception {
        try (ServerSocket nameServer = listeningSocket();
                ServerSocket broker = listeningSocket();
                ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<RemotingCommand> nameServerRequest = respondSuccessfully(nameServer, executor);
            Future<RemotingCommand> brokerRequest = respondSuccessfully(broker, executor);
            RocketmqReadinessHealthIndicator indicator = new RocketmqReadinessHealthIndicator(
                    properties(
                            List.of(endpoint("127.0.0.1", nameServer.getLocalPort())),
                            List.of(endpoint("127.0.0.1", broker.getLocalPort()))
                    )
            );

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertReadOnlyProbe(nameServerRequest.get(), RequestCode.GET_NAMESRV_CONFIG);
            assertReadOnlyProbe(brokerRequest.get(), RequestCode.GET_BROKER_RUNTIME_INFO);
        }
    }

    @Test
    void failsClosedWhenTcpProxyAcceptsButRocketmqDoesNotReply() throws Exception {
        try (ServerSocket silentNameServer = listeningSocket();
                ServerSocket broker = listeningSocket();
                ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> acceptedConnection = executor.submit(() -> {
                try (Socket ignored = silentNameServer.accept()) {
                    Thread.sleep(500);
                }
                return null;
            });
            RocketmqReadinessHealthIndicator indicator = new RocketmqReadinessHealthIndicator(
                    properties(
                            List.of(endpoint("127.0.0.1", silentNameServer.getLocalPort())),
                            List.of(endpoint("127.0.0.1", broker.getLocalPort()))
                    )
            );

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).isEmpty();
            acceptedConnection.get();
        }
    }

    @Test
    void requiresOneReachableEndpointFromEachSetAndStopsAfterFirstSuccess() {
        RecordingProbe probe = new RecordingProbe();
        probe.fail("namesrv-a", 9876);
        probe.reachable("namesrv-b", 9876);
        probe.reachable("broker-a", 10911);
        RocketmqReadinessHealthIndicator indicator = new RocketmqReadinessHealthIndicator(
                properties(
                        List.of(endpoint("namesrv-a", 9876), endpoint("namesrv-b", 9876)),
                        List.of(endpoint("broker-a", 10911), endpoint("broker-b", 10911))
                ),
                probe
        );

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).isEmpty();
        assertThat(probe.attempts()).containsExactly(
                new Attempt("namesrv-a", 9876, Duration.ofMillis(250)),
                new Attempt("namesrv-b", 9876, Duration.ofMillis(250)),
                new Attempt("broker-a", 10911, Duration.ofMillis(250))
        );
    }

    @Test
    void failsClosedWithoutDetailsAndRecoversOnTheNextProbe() {
        RecordingProbe probe = new RecordingProbe();
        probe.reachable("namesrv", 9876);
        RocketmqReadinessHealthIndicator indicator = new RocketmqReadinessHealthIndicator(
                properties(
                        List.of(endpoint("namesrv", 9876)),
                        List.of(endpoint("internal-broker", 10911))
                ),
                probe
        );

        Health unavailable = indicator.health();
        probe.reachable("internal-broker", 10911);
        Health recovered = indicator.health();

        assertThat(unavailable.getStatus()).isEqualTo(Status.DOWN);
        assertThat(unavailable.getDetails()).isEmpty();
        assertThat(unavailable.toString())
                .doesNotContain("internal-broker")
                .doesNotContain("refused")
                .doesNotContain("endpoint");
        assertThat(recovered.getStatus()).isEqualTo(Status.UP);
        assertThat(recovered.getDetails()).isEmpty();
    }

    private static RocketmqReadinessProperties properties(
            List<RocketmqReadinessEndpoint> nameServers,
            List<RocketmqReadinessEndpoint> brokers
    ) {
        RocketmqReadinessProperties properties = new RocketmqReadinessProperties();
        properties.setNameServers(nameServers);
        properties.setBrokers(brokers);
        properties.setConnectTimeout(Duration.ofMillis(250));
        return properties;
    }

    private static ServerSocket listeningSocket() throws IOException {
        ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        socket.setSoTimeout(1_000);
        return socket;
    }

    private static Future<RemotingCommand> respondSuccessfully(
            ServerSocket server,
            ExecutorService executor
    ) {
        return executor.submit(() -> {
            try (Socket connection = server.accept()) {
                connection.setSoTimeout(1_000);
                RemotingCommand request = readCommand(connection);
                RemotingCommand response = RemotingCommand.createResponseCommand(ResponseCode.SUCCESS, null);
                response.setOpaque(request.getOpaque());
                writeCommand(connection, response);
                return request;
            }
        });
    }

    private static RemotingCommand readCommand(Socket connection) throws Exception {
        DataInputStream input = new DataInputStream(connection.getInputStream());
        int frameLength = input.readInt();
        return RemotingCommand.decode(ByteBuffer.wrap(input.readNBytes(frameLength)));
    }

    private static void writeCommand(Socket connection, RemotingCommand command) throws IOException {
        ByteBuffer frame = command.encode();
        DataOutputStream output = new DataOutputStream(connection.getOutputStream());
        output.write(frame.array(), frame.position(), frame.remaining());
        output.flush();
    }

    private static void assertReadOnlyProbe(RemotingCommand request, int expectedCode) {
        assertThat(request.getCode()).isEqualTo(expectedCode);
        assertThat(request.getBody()).isNullOrEmpty();
    }

    private static RocketmqReadinessEndpoint endpoint(String host, int port) {
        RocketmqReadinessEndpoint endpoint = new RocketmqReadinessEndpoint();
        endpoint.setHost(host);
        endpoint.setPort(port);
        return endpoint;
    }

    private record Attempt(String host, int port, Duration timeout) {
    }

    private static final class RecordingProbe implements BiPredicate<RocketmqReadinessEndpoint, Duration> {
        private final Set<String> reachable = new HashSet<>();
        private final Set<String> failures = new HashSet<>();
        private final List<Attempt> attempts = new ArrayList<>();

        @Override
        public boolean test(
                RocketmqReadinessEndpoint endpoint,
                Duration timeout
        ) {
            attempts.add(new Attempt(endpoint.getHost(), endpoint.getPort(), timeout));
            String key = key(endpoint.getHost(), endpoint.getPort());
            if (failures.contains(key)) {
                throw new UncheckedIOException(new IOException(
                        "endpoint=" + endpoint.getHost() + ", refused"
                ));
            }
            return reachable.contains(key);
        }

        void reachable(String host, int port) {
            failures.remove(key(host, port));
            reachable.add(key(host, port));
        }

        void fail(String host, int port) {
            reachable.remove(key(host, port));
            failures.add(key(host, port));
        }

        List<Attempt> attempts() {
            return List.copyOf(attempts);
        }

        private static String key(String host, int port) {
            return host + ":" + port;
        }
    }
}

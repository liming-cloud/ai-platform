package com.aiplatform.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class RuntimeDependencyConvergenceTest {
    private static final List<String> NETTY_ARTIFACTS = List.of(
            "netty-common",
            "netty-handler",
            "netty-handler-proxy",
            "netty-buffer",
            "netty-codec",
            "netty-codec-http",
            "netty-codec-http2",
            "netty-transport-native-unix-common",
            "netty-transport-native-epoll",
            "netty-transport-native-kqueue");

    @Test
    void unshadedNettyRuntimeIsConvergedOnTheSpringBootFourGeneration() throws IOException {
        // grpc-netty-shaded carries isolated Netty metadata, so verify actual io.netty Maven artifacts.
        List<String> nettyBaseline = mavenVersions("io.netty", "netty-common");
        assertThat(nettyBaseline)
                .hasSize(1)
                .allMatch(version -> version.matches("4\\.2\\.\\d+\\.Final"));
        for (String artifactId : NETTY_ARTIFACTS) {
            assertThat(mavenVersions("io.netty", artifactId))
                    .as(artifactId)
                    .containsOnlyElementsOf(nettyBaseline);
        }
    }

    @Test
    void springFrameworkSevenUsesOneConvergedCommonsLoggingImplementation() throws IOException {
        List<String> logFactoryLocations = Collections.list(Thread.currentThread()
                        .getContextClassLoader()
                        .getResources("org/apache/commons/logging/LogFactory.class"))
                .stream()
                .map(URL::toExternalForm)
                .toList();

        assertThat(logFactoryLocations)
                .singleElement()
                .asString()
                .contains("commons-logging-1.3.6.jar");
        assertThat(mavenVersions("commons-logging", "commons-logging"))
                .containsOnly("1.3.6");
    }

    private static List<String> mavenVersions(String groupId, String artifactId) throws IOException {
        String resource = "META-INF/maven/%s/%s/pom.properties".formatted(groupId, artifactId);
        List<URL> locations = Collections.list(
                Thread.currentThread().getContextClassLoader().getResources(resource));
        assertThat(locations).as(resource).isNotEmpty();

        List<String> versions = new ArrayList<>();
        for (URL location : locations) {
            Properties properties = new Properties();
            try (var input = location.openStream()) {
                properties.load(input);
            }
            versions.add(properties.getProperty("version"));
        }
        return versions;
    }
}

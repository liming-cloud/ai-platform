package com.aiplatform.infra.springai.test;

import com.aiplatform.infra.springai.http.SecureModelHttpTransport;
import com.aiplatform.shared.security.net.OutboundEndpointPolicy;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

/** 测试专用传输：仅用于既有本地 HTTP 协议夹具，生产配置不会加载。 */
public final class TestModelHttpTransport {
    private TestModelHttpTransport() {
    }

    public static SecureModelHttpTransport permissiveLocalTransport() {
        return new SecureModelHttpTransport(new OutboundEndpointPolicy() {
            @Override
            public URI validateAndNormalize(String endpoint) {
                return URI.create(endpoint.trim());
            }

            @Override
            public List<InetAddress> resolveForConnection(String hostname) throws UnknownHostException {
                return List.of(InetAddress.getAllByName(hostname));
            }
        });
    }
}

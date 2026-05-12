package org.fujay.ai.platform.model.application.port;

public interface ModelGatewayPort {

    ModelGatewayChatResult testChat(ModelGatewayChatRequest request);

    ModelGatewayChatResult invokeChat(ModelGatewayChatRequest request);
}

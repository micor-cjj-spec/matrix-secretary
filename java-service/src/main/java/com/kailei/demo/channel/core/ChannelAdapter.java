package com.kailei.demo.channel.core;

import com.kailei.demo.channel.model.ChannelIncomingMessage;
import com.kailei.demo.channel.model.ChannelOutgoingMessage;
import com.kailei.demo.channel.model.ChannelPlatform;

import java.util.Map;
import java.util.Optional;

public interface ChannelAdapter {

    ChannelPlatform platform();

    Optional<ChannelIncomingMessage> parseIncoming(Map<String, Object> rawEvent);

    void sendText(ChannelOutgoingMessage message);
}

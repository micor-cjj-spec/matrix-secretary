package com.kailei.demo.channel.core;

import com.kailei.demo.channel.model.ChannelPlatform;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ChannelAdapterRegistry {

    private final Map<ChannelPlatform, ChannelAdapter> adapters = new EnumMap<>(ChannelPlatform.class);

    public ChannelAdapterRegistry(List<ChannelAdapter> channelAdapters) {
        for (ChannelAdapter adapter : channelAdapters) {
            adapters.put(adapter.platform(), adapter);
        }
    }

    public Optional<ChannelAdapter> find(ChannelPlatform platform) {
        return Optional.ofNullable(adapters.get(platform));
    }
}

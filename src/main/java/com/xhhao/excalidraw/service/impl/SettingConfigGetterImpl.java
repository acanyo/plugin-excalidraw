package com.xhhao.excalidraw.service.impl;

import com.xhhao.excalidraw.service.SettingConfigGetter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;


@Component
@RequiredArgsConstructor
public class SettingConfigGetterImpl implements SettingConfigGetter {
    
    private final ReactiveSettingFetcher settingFetcher;

    @Override
    public Mono<AttachmentConfig> getAttachmentConfig() {
        return settingFetcher.fetch(AttachmentConfig.GROUP, AttachmentConfig.class)
            .defaultIfEmpty(new AttachmentConfig());
    }
}

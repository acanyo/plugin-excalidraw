package com.xhhao.excalidraw.service;

import lombok.Data;
import reactor.core.publisher.Mono;

public interface SettingConfigGetter {
    
    Mono<AttachmentConfig> getAttachmentConfig();

    @Data
    class AttachmentConfig {
        public static final String GROUP = "attachment";
        private AttachmentSettings attachmentSettings;
    }
    
    @Data
    class AttachmentSettings {
        private String filePolicy;
        private String fileGroup;
    }
}

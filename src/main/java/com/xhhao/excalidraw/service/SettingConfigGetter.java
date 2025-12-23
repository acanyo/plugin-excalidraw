package com.xhhao.excalidraw.service;

import lombok.Data;
import reactor.core.publisher.Mono;

public interface SettingConfigGetter {
    
    Mono<BasicConfig> getBasicConfig();

    @Data
    class BasicConfig {
        public static final String GROUP = "basic";
        
        private PreviewSettings previewSettings;
        private AttachmentSettings attachmentSettings;
    }
    
    @Data
    class PreviewSettings {
        private String previewFormat = "svg";
        
        public boolean isPng() {
            return "png".equalsIgnoreCase(previewFormat);
        }
    }
    
    @Data
    class AttachmentSettings {
        private String filePolicy;
        private String fileGroup;
    }
}

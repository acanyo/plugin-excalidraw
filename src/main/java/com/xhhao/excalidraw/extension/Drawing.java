package com.xhhao.excalidraw.extension;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * Excalidraw 绘图扩展实体
 *
 * @author Handsome
 */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "excalidraw.xhhao.com", version = "v1alpha1", kind = "Drawing",
    plural = "drawings", singular = "drawing")
public class Drawing extends AbstractExtension {

    @Schema(requiredMode = REQUIRED)
    private DrawingSpec spec;

    @Data
    public static class DrawingSpec {
        /**
         * 绘图名称
         */
        private String displayName;

        /**
         * Excalidraw 绘图数据 (JSON)
         */
        private String data;
        
        /**
         * 预览图 URL（存储在附件库，支持 SVG 和 PNG）
         */
        private String previewUrl;
        
        /**
         * 预览格式: svg 或 png
         */
        private String previewFormat;
        
        /**
         * 预览图附件的 metadata.name，用于更新时删除旧附件
         */
        private String previewAttachmentName;
    }
}

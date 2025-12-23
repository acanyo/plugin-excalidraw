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
         * SVG 预览数据
         */
        private String svg;
    }
}

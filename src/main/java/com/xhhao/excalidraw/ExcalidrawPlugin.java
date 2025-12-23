package com.xhhao.excalidraw;

import static run.halo.app.extension.index.IndexAttributeFactory.simpleAttribute;

import com.xhhao.excalidraw.extension.Drawing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpec;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * Excalidraw 插件主类
 *
 * @author Handsome
 */
@Slf4j
@Component
public class ExcalidrawPlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public ExcalidrawPlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        schemeManager.register(Drawing.class, indexSpecs -> 
            indexSpecs.add(new IndexSpec()
                .setName("spec.displayName")
                .setIndexFunc(simpleAttribute(Drawing.class,
                    drawing -> drawing.getSpec().getDisplayName())))
        );
        log.info("Excalidraw 插件启动成功！");
    }

    @Override
    public void stop() {
        schemeManager.unregister(Scheme.buildFromType(Drawing.class));
        log.info("Excalidraw 插件已停止！");
    }
}

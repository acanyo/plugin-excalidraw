package com.xhhao.excalidraw.service.impl;

import com.xhhao.excalidraw.ExcalidrawQuery;
import com.xhhao.excalidraw.extension.Drawing;
import com.xhhao.excalidraw.service.ExcalidrawService;
import com.xhhao.excalidraw.service.SettingConfigGetter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.attachment.endpoint.SimpleFilePart;
import run.halo.app.core.extension.service.AttachmentService;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcalidrawServiceImpl implements ExcalidrawService {

    private final ReactiveExtensionClient client;
    private final SettingConfigGetter settingConfigGetter;
    private final AttachmentService attachmentService;
    @Override
    public Mono<ListResult<Drawing>> listDrawings(ExcalidrawQuery query) {
        return client.listBy(Drawing.class, query.toListOptions(),
            PageRequestImpl.of(query.getPage(), query.getSize(), query.getSort()));
    }

    @Override
    public Mono<String> uploadExcalidrawFile(String fileName, String jsonContent, String userName) {
        return settingConfigGetter.getAttachmentConfig()
            .flatMap(config -> {
                var settings = config.getAttachmentSettings();
                if (settings == null || settings.getFilePolicy() == null || settings.getFilePolicy().isBlank()) {
                    log.info("未配置附件存储策略，跳过 .excalidraw 文件上传");
                    return Mono.just("未配置存储策略");
                }
                if (userName == null || userName.isBlank()) {
                    return org.springframework.security.core.context.ReactiveSecurityContextHolder.getContext()
                        .map(ctx -> ctx.getAuthentication().getName())
                        .flatMap(currentUser -> doUploadExcalidrawFile(fileName, jsonContent, currentUser, settings));
                }
                return doUploadExcalidrawFile(fileName, jsonContent, userName, settings);
            })
            .defaultIfEmpty("");
    }

    private Mono<String> doUploadExcalidrawFile(String fileName, String jsonContent,
                                                 String userName, SettingConfigGetter.AttachmentSettings settings) {
        var fullFileName = fileName + ".excalidraw";
        var contentBytes = jsonContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var dataBuffer = DefaultDataBufferFactory.sharedInstance.wrap(contentBytes);
        Flux<DataBuffer> dataBufferFlux = Flux.just(dataBuffer);
        var file = new SimpleFilePart(fullFileName, dataBufferFlux, org.springframework.http.MediaType.APPLICATION_JSON);

        return attachmentService.upload(userName, settings.getFilePolicy(), settings.getFileGroup(), file, null)
            .subscribeOn(Schedulers.boundedElastic())
            .map(attachment -> {
                log.info(".excalidraw 文件上传成功: {}", fullFileName);
                return "ok";
            })
            .onErrorResume(e -> {
                log.error("上传 .excalidraw 文件失败: {}", e.getMessage(), e);
                return Mono.just("");
            });
    }
}

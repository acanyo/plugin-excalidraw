package com.xhhao.excalidraw.service.impl;

import com.xhhao.excalidraw.ExcalidrawQuery;
import com.xhhao.excalidraw.extension.Drawing;
import com.xhhao.excalidraw.service.ExcalidrawService;
import com.xhhao.excalidraw.service.SettingConfigGetter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.attachment.Attachment;
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

    private Mono<String> getCurrentUserName() {
        return ReactiveSecurityContextHolder.getContext()
            .map(ctx -> ctx.getAuthentication().getName())
            .defaultIfEmpty("admin");
    }

    @Override
    public Mono<String> uploadExcalidrawFile(String fileName, String jsonContent, String userName) {
        return getCurrentUserName()
            .flatMap(currentUser -> settingConfigGetter.getBasicConfig()
                .flatMap(config -> {
                    var settings = config.getAttachmentSettings();
                    if (settings == null || settings.getFilePolicy() == null || settings.getFilePolicy().isBlank()) {
                        log.info("未配置附件存储策略，跳过 .excalidraw 文件上传");
                        return Mono.just("未配置存储策略");
                    }
                    return doUploadExcalidrawFile(fileName, jsonContent, currentUser, settings);
                })
            )
            .defaultIfEmpty("");
    }

    private Mono<String> doUploadExcalidrawFile(String fileName, String jsonContent,
                                                 String userName, SettingConfigGetter.AttachmentSettings settings) {
        var fullFileName = fileName + ".excalidraw";
        var contentBytes = jsonContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var dataBuffer = DefaultDataBufferFactory.sharedInstance.wrap(contentBytes);
        Flux<DataBuffer> dataBufferFlux = Flux.just(dataBuffer);
        var file = new SimpleFilePart(fullFileName, dataBufferFlux, MediaType.APPLICATION_JSON);

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

    @Override
    public Mono<UploadResult> uploadPreviewImage(String fileName, String content, String format, String userName, String oldAttachmentName) {
        return getCurrentUserName()
            .flatMap(currentUser -> settingConfigGetter.getBasicConfig()
                .flatMap(config -> {
                    log.info("当前用户: {}", currentUser);
                    var settings = config.getAttachmentSettings();
                    if (settings == null || settings.getFilePolicy() == null || settings.getFilePolicy().isBlank()) {
                        log.info("未配置附件存储策略，跳过预览图上传");
                        return Mono.just(new UploadResult("未配置存储策略", null));
                    }
                    return doUploadPreviewImage(fileName, content, format, currentUser, settings, oldAttachmentName);
                })
            )
            .defaultIfEmpty(new UploadResult("", null));
    }

    private Mono<UploadResult> doUploadPreviewImage(String fileName, String content, String format,
                                               String userName, SettingConfigGetter.AttachmentSettings settings, String oldAttachmentName) {
        boolean isSvg = "svg".equalsIgnoreCase(format);
        var fullFileName = fileName + (isSvg ? ".svg" : ".png");
        MediaType mediaType = isSvg
            ? MediaType.valueOf("image/svg+xml")
            : MediaType.IMAGE_PNG;
        
        // 先删除旧附件（如果有）
        Mono<Void> deleteOld = (oldAttachmentName != null && !oldAttachmentName.isBlank())
            ? deleteAttachmentByName(oldAttachmentName)
            : Mono.empty();
            
        return deleteOld.then(Mono.defer(() -> {
                Flux<DataBuffer> dataBufferFlux;
                if (isSvg) {
                    dataBufferFlux = Flux.just(DefaultDataBufferFactory.sharedInstance
                        .wrap(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                } else {
                    String pureBase64 = content.contains(",") 
                        ? content.substring(content.indexOf(",") + 1) 
                        : content;
                    dataBufferFlux = Flux.just(DefaultDataBufferFactory.sharedInstance
                        .wrap(java.util.Base64.getDecoder().decode(pureBase64)));
                }
                
                var file = new SimpleFilePart(fullFileName, dataBufferFlux, mediaType);
                return attachmentService.upload(userName, settings.getFilePolicy(), settings.getFileGroup(), file, null)
                    .subscribeOn(Schedulers.boundedElastic())
                    .map(attachment -> {
                        var annotations = attachment.getMetadata().getAnnotations();
                        String url = annotations.get("storage.halo.run/uri");
                        if (url == null) {
                            url = annotations.get("storage.halo.run/external-link");
                        }
                        String attachmentName = attachment.getMetadata().getName();
                        return new UploadResult(url != null ? url : "", attachmentName);
                    });
            }))
            .onErrorResume(e -> {
                log.error("上传预览图失败: {}", e.getMessage(), e);
                return Mono.just(new UploadResult("", null));
            });
    }
    
    private Mono<Void> deleteAttachmentByName(String attachmentName) {
        return client.get(Attachment.class, attachmentName)
            .flatMap(client::delete)
            .then()
            .onErrorResume(e -> {
                log.warn("删除旧附件失败: {}", e.getMessage());
                return Mono.empty();
            });
    }

    @Override
    public Mono<String> getPreviewFormat() {
        return settingConfigGetter.getBasicConfig()
            .map(config -> {
                var settings = config.getPreviewSettings();
                if (settings == null || settings.getPreviewFormat() == null) {
                    return "svg";
                }
                return settings.getPreviewFormat();
            })
            .defaultIfEmpty("svg");
    }
}

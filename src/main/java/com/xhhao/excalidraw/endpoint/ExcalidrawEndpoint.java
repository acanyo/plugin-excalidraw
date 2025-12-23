package com.xhhao.excalidraw.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import com.xhhao.excalidraw.ExcalidrawQuery;
import com.xhhao.excalidraw.extension.Drawing;
import com.xhhao.excalidraw.service.ExcalidrawService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListResult;

/**
 * Excalidraw 绘图管理端点
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExcalidrawEndpoint implements CustomEndpoint {

    private final ExcalidrawService excalidrawService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "api.excalidraw.xhhao.com/v1alpha1/Drawing";
        return SpringdocRouteBuilder.route()
            .GET("drawings", this::listDrawings, builder -> {
                    builder.operationId("ListDrawings")
                        .tag(tag)
                        .description("分页查询绘图列表")
                        .response(
                            responseBuilder()
                                .implementation(ListResult.generateGenericClass(Drawing.class))
                        );
                    ExcalidrawQuery.buildParameters(builder);
                }
            )
            .POST("drawings/upload", this::uploadExcalidrawFile, builder -> 
                builder.operationId("UploadExcalidrawFile")
                    .tag(tag)
                    .description("上传 .excalidraw 文件到附件库")
                    .requestBody(requestBodyBuilder().implementation(UploadRequest.class))
                    .response(responseBuilder().implementation(UploadResponse.class))
            )
            .POST("drawings/upload-preview", this::uploadPreviewImage, builder -> 
                builder.operationId("UploadPreviewImage")
                    .tag(tag)
                    .description("上传预览图到附件库（支持 SVG 和 PNG）")
                    .requestBody(requestBodyBuilder().implementation(UploadPreviewRequest.class))
                    .response(responseBuilder().implementation(UploadResponse.class))
            )
            .GET("settings/preview-format", this::getPreviewFormat, builder ->
                builder.operationId("GetPreviewFormat")
                    .tag(tag)
                    .description("获取预览格式设置")
                    .response(responseBuilder().implementation(PreviewFormatResponse.class))
            )
            .build();
    }

    Mono<ServerResponse> listDrawings(ServerRequest serverRequest) {
        ExcalidrawQuery query = new ExcalidrawQuery(serverRequest);
        return excalidrawService.listDrawings(query)
            .flatMap(drawings -> ServerResponse.ok().bodyValue(drawings));
    }

    Mono<ServerResponse> uploadExcalidrawFile(ServerRequest request) {
        return request.bodyToMono(UploadRequest.class)
            .flatMap(req -> excalidrawService.uploadExcalidrawFile(
                req.getFileName(), 
                req.getJsonContent(), 
                req.getUserName()
            ))
            .flatMap(url -> ServerResponse.ok().bodyValue(new UploadResponse(url, null)))
            .switchIfEmpty(ServerResponse.ok().bodyValue(new UploadResponse("", null)));
    }

    Mono<ServerResponse> uploadPreviewImage(ServerRequest request) {
        return request.bodyToMono(UploadPreviewRequest.class)
            .flatMap(req -> excalidrawService.uploadPreviewImage(
                req.getFileName(),
                req.getContent(),
                req.getFormat(),
                req.getUserName(),
                req.getOldAttachmentName()
            ))
            .flatMap(result -> ServerResponse.ok().bodyValue(new UploadResponse(result.getUrl(), result.getAttachmentName())))
            .switchIfEmpty(ServerResponse.ok().bodyValue(new UploadResponse("", null)));
    }

    Mono<ServerResponse> getPreviewFormat(ServerRequest request) {
        return excalidrawService.getPreviewFormat()
            .flatMap(format -> ServerResponse.ok().bodyValue(new PreviewFormatResponse(format)));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.excalidraw.xhhao.com/v1alpha1");
    }

    @Data
    public static class UploadRequest {
        private String fileName;
        private String jsonContent;
        private String userName;
    }

    @Data
    public static class UploadResponse {
        private final String url;
        private final String attachmentName;
        public UploadResponse(String url, String attachmentName) {
            this.url = url;
            this.attachmentName = attachmentName;
        }
    }

    @Data
    public static class UploadPreviewRequest {
        private String fileName;
        private String content;  // SVG 字符串或 PNG base64
        private String format;   // svg 或 png
        private String userName;
        private String oldAttachmentName;  // 旧附件名称，用于删除
    }

    @Data
    public static class PreviewFormatResponse {
        private final String format;
        public PreviewFormatResponse(String format) {
            this.format = format;
        }
    }
}

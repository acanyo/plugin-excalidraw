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
            .flatMap(url -> ServerResponse.ok().bodyValue(new UploadResponse(url)))
            .switchIfEmpty(ServerResponse.ok().bodyValue(new UploadResponse("")));
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
        public UploadResponse(String url) {
            this.url = url;
        }
    }
}

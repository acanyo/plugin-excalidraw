package com.xhhao.excalidraw;

import com.xhhao.excalidraw.extension.Drawing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.theme.ReactivePostContentHandler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExcalidrawContentHandler implements ReactivePostContentHandler {

    private final ReactiveExtensionClient client;

    // 匹配 <excalidraw-drawing drawingName="xxx" ...> 或 <excalidraw-drawing drawingname="xxx" ...>
    private static final Pattern EXCALIDRAW_PATTERN = Pattern.compile(
        "<excalidraw-drawing[^>]*?(?:drawingName|drawingname)\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?>(?:</excalidraw-drawing>)?",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public Mono<PostContentContext> handle(PostContentContext context) {
        String content = context.getContent();
        if (content == null || !content.contains("excalidraw-drawing")) {
            return Mono.just(context);
        }

        return replaceExcalidrawTags(content)
            .map(newContent -> {
                context.setContent(newContent);
                return context;
            });
    }

    private Mono<String> replaceExcalidrawTags(String content) {
        Matcher matcher = EXCALIDRAW_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder();
        return processMatches(content, matcher, result, 0);
    }

    private Mono<String> processMatches(String content, Matcher matcher, 
                                         StringBuilder result, int lastEnd) {
        if (!matcher.find()) {
            result.append(content.substring(lastEnd));
            return Mono.just(result.toString());
        }

        String drawingName = matcher.group(1);
        int matchStart = matcher.start();
        int matchEnd = matcher.end();

        // 添加匹配之前的内容
        result.append(content, lastEnd, matchStart);

        return getDrawingSvg(drawingName)
            .defaultIfEmpty(createPlaceholder(drawingName))
            .flatMap(svg -> {
                result.append(svg);
                return processMatches(content, matcher, result, matchEnd);
            });
    }

    private Mono<String> getDrawingSvg(String drawingName) {
        return client.fetch(Drawing.class, drawingName)
            .map(drawing -> {
                String svg = drawing.getSpec().getSvg();
                if (svg != null && !svg.isBlank()) {
                    return wrapSvg(svg, drawing.getSpec().getDisplayName());
                }
                return createPlaceholder(drawingName);
            })
            .onErrorResume(e -> {
                log.warn("加载绘图 {} 失败: {}", drawingName, e.getMessage());
                return Mono.just(createPlaceholder(drawingName));
            });
    }

    private static final String CONTAINER_STYLE = 
        "width:100%;max-width:100%;margin:1rem 0;overflow-x:auto;";
    private static final String SVG_STYLE = 
        "max-width:100%;height:auto;display:block;";

    private String wrapSvg(String svg, String displayName) {
        // 内联样式，插件自包含不依赖主题
        String processedSvg = svg.replaceFirst("<svg ", "<svg style=\"" + SVG_STYLE + "\" ");
        return String.format(
            "<figure class=\"excalidraw-drawing\" data-name=\"%s\" style=\"%s\">%s</figure>",
            escapeHtml(displayName != null ? displayName : ""),
            CONTAINER_STYLE,
            processedSvg
        );
    }

    private String createPlaceholder(String drawingName) {
        return String.format(
            "<div class=\"excalidraw-drawing excalidraw-placeholder\" data-name=\"%s\">" +
            "<span>绘图加载失败</span></div>",
            escapeHtml(drawingName)
        );
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}

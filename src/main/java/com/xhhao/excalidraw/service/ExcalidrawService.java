package com.xhhao.excalidraw.service;

import com.xhhao.excalidraw.ExcalidrawQuery;
import com.xhhao.excalidraw.extension.Drawing;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListResult;


public interface ExcalidrawService {

    /**
     * 分页查询绘图列表
     */
    Mono<ListResult<Drawing>> listDrawings(ExcalidrawQuery query);
    
    /**
     * 上传 .excalidraw 文件到附件库
     * @param fileName 文件名（不含扩展名）
     * @param jsonContent 绘图 JSON 数据
     * @param userName 用户名
     * @return 上传后的附件库URL，如果未启用附件存储则返回空
     */
    Mono<String> uploadExcalidrawFile(String fileName, String jsonContent, String userName);

    /**
     * 上传预览图到附件库（支持 SVG 和 PNG）
     * @param fileName 文件名（不含扩展名）
     * @param content 图片内容（PNG 为 base64，SVG 为字符串）
     * @param format 格式：svg 或 png
     * @param userName 用户名
     * @param oldAttachmentName 旧附件名称，用于删除
     * @return 上传结果（包含 URL 和附件名称）
     */
    Mono<UploadResult> uploadPreviewImage(String fileName, String content, String format, String userName, String oldAttachmentName);

    /**
     * 获取预览格式设置
     * @return 预览格式 (svg 或 png)
     */
    Mono<String> getPreviewFormat();
    
    @lombok.Data
    @lombok.AllArgsConstructor
    class UploadResult {
        private String url;
        private String attachmentName;
    }
}

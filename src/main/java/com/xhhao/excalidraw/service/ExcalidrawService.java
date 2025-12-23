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
}

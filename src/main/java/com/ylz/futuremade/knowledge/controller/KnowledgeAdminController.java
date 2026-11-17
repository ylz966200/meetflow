package com.ylz.futuremade.knowledge.controller;

import com.ylz.futuremade.knowledge.model.dto.MilvusConnectionView;
import com.ylz.futuremade.knowledge.service.MilvusConnectionService;
import com.ylz.futuremade.knowledge.service.EmbeddingPreviewService;
import com.ylz.futuremade.knowledge.model.dto.EmbeddingPreviewRequest;
import com.ylz.futuremade.knowledge.model.dto.EmbeddingPreviewView;
import com.ylz.futuremade.knowledge.model.dto.MilvusDemoRequest;
import com.ylz.futuremade.knowledge.model.dto.MilvusDemoView;
import com.ylz.futuremade.knowledge.service.MilvusDemoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 知识库管理员接口，提供 Milvus 只读连接检查。
 */
@RestController
@RequestMapping("/api/admin/knowledge")
public class KnowledgeAdminController {
    private final MilvusConnectionService connection;
    private final EmbeddingPreviewService embeddings;
    private final MilvusDemoService demo;

    public KnowledgeAdminController(MilvusConnectionService connection, EmbeddingPreviewService embeddings, MilvusDemoService demo) {
        this.connection = connection;
        this.embeddings = embeddings;
        this.demo = demo;
    }

    /**
     * 管理员检查 Zilliz Cloud 是否可连接。
     */
    @GetMapping("/milvus/connection")
    public MilvusConnectionView connection() {
        return connection.check();
    }

    /**
     * 管理员对实验文本进行向量化；不会写入向量库。
     */
    @PostMapping("/embedding/preview")
    public EmbeddingPreviewView preview(@RequestBody EmbeddingPreviewRequest request) {
        return embeddings.preview(request.getText());
    }

    /** 使用固定虚构数据运行向量写入与检索；调用会产生模型用量。 */
    @PostMapping("/milvus/demo")
    public MilvusDemoView demo(@RequestBody MilvusDemoRequest request) {
        return demo.run(request.getQuery());
    }
}

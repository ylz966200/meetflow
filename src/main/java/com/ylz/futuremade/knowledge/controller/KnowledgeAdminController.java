package com.ylz.futuremade.knowledge.controller;

import com.ylz.futuremade.knowledge.model.dto.MilvusConnectionView;
import com.ylz.futuremade.knowledge.service.MilvusConnectionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 知识库管理员接口，检查 Milvus 服务连接。 */
@RestController
@RequestMapping("/api/admin/knowledge")
public class KnowledgeAdminController {
    private final MilvusConnectionService connection;
    public KnowledgeAdminController(MilvusConnectionService connection) { this.connection = connection; }
    /** 管理员检查 Zilliz Cloud 是否可连接。 */
    @GetMapping("/milvus/connection")
    public MilvusConnectionView connection() { return connection.check(); }
}

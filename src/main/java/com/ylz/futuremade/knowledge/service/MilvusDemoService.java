package com.ylz.futuremade.knowledge.service;

import com.ylz.futuremade.common.logging.ErrorDiagnostics;
import com.ylz.futuremade.knowledge.model.dto.MilvusDemoView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 使用固定的虚构会议文本演示 Embedding 与 Milvus 的完整检索流程。 */
@Service
public class MilvusDemoService {
    private static final List<String> SAMPLE_TEXTS = List.of(
            "年会预算最终上限为八万元。",
            "王丽负责收集活动场地的报价。",
            "场地确定后制作年会宣传海报。");
    private final ObjectProvider<EmbeddingModel> models;
    private final MilvusDemoStore store;
    private final String modelId;

    public MilvusDemoService(@Qualifier("doubaoEmbeddingModel") ObjectProvider<EmbeddingModel> models,
            MilvusDemoStore store, @Value("${meetflow.ai.doubao.embedding.model:}") String modelId) {
        this.models = models;
        this.store = store;
        this.modelId = modelId;
    }

    /** 显式执行实验：每次重新计算三个样本和一个问题的向量，不涉及真实会议。 */
    public MilvusDemoView run(String query) {
        if (query == null || query.isBlank() || query.length() > 1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入不超过 1000 字符的实验问题");
        var model = models.getIfAvailable();
        if (model == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "豆包向量模型未启用");
        try {
            var vectors = model.embed(SAMPLE_TEXTS);
            float[] queryVector = model.embed(query);
            int dimension = queryVector.length;
            if (dimension == 0 || vectors.size() != SAMPLE_TEXTS.size() || vectors.stream().anyMatch(vector -> vector.length != dimension))
                throw new IllegalStateException("实验向量数量或维度不一致");
            String identity = fingerprint(modelId);
            String collection = "meetflow_demo_" + identity + "_" + dimension;
            String marker = "meetflow-demo-v1:" + identity + ":" + dimension;
            var hits = store.search(collection, marker, dimension, SAMPLE_TEXTS, vectors, queryVector);
            var sample = new ArrayList<Float>();
            for (int i = 0; i < Math.min(8, queryVector.length); i++) sample.add(queryVector[i]);
            var result = new MilvusDemoView();
            result.setCollection(collection);
            result.setDimension(dimension);
            result.setSampleCount(SAMPLE_TEXTS.size());
            result.setVectorSample(sample);
            result.setHits(hits);
            return result;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            ErrorDiagnostics.error("Milvus 虚构数据实验失败", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "实验失败，请检查方舟模型、Zilliz 集群状态、集合配额和访问权限");
        }
    }

    /** 用模型或接入点 ID 的摘要隔离不同模型的实验集合。 */
    private String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("当前环境不支持 SHA-256", ex); }
    }
}

package com.ylz.futuremade.assistant.service;

import com.ylz.futuremade.assistant.model.dto.ArkEmbeddingRequest;
import com.ylz.futuremade.assistant.model.dto.ArkEmbeddingResponse;
import java.util.ArrayList;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** 将方舟多模态向量接口适配为 Spring AI 文本向量模型。 */
public class ArkMultimodalEmbeddingModel extends AbstractEmbeddingModel {
    /** 方舟多模态向量路径，相对于已配置的 API 基础地址。 */
    private static final String EMBEDDINGS_PATH = "/embeddings/multimodal";
    private final RestClient client;
    private final String model;

    public ArkMultimodalEmbeddingModel(RestClient client, String model) {
        this.client = client;
        this.model = model;
    }

    /** 对每条文本独立调用：多模态接口的 input 列表代表一次合并输入，而非普通文本批次。 */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        if (request.getOptions() != null && (request.getOptions().getDimensions() != null
                || request.getOptions().getModel() != null && !model.equals(request.getOptions().getModel())))
            throw new IllegalArgumentException("当前方舟向量适配器不支持按请求切换模型或维度");
        var results = new ArrayList<Embedding>();
        for (String text : request.getInstructions()) {
            var response = client.post().uri(EMBEDDINGS_PATH).contentType(MediaType.APPLICATION_JSON)
                    .body(new ArkEmbeddingRequest(model, text)).retrieve().body(ArkEmbeddingResponse.class);
            if (response == null || response.getData() == null || response.getData().getEmbedding() == null
                    || response.getData().getEmbedding().length == 0)
                throw new IllegalStateException("方舟未返回有效的稠密向量");
            float[] vector = response.getData().getEmbedding();
            embeddingDimensions.compareAndSet(-1, vector.length);
            if (embeddingDimensions.get() != vector.length) throw new IllegalStateException("方舟返回的向量维度不一致");
            results.add(new Embedding(vector, results.size()));
        }
        return new EmbeddingResponse(results);
    }

    /** 文档正文参与向量化，不自动拼接业务元数据。 */
    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }
}

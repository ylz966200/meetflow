package com.ylz.futuremade.knowledge.service;

import com.ylz.futuremade.common.logging.ErrorDiagnostics;
import com.ylz.futuremade.knowledge.model.dto.EmbeddingPreviewView;
import java.util.ArrayList;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 对管理员提供的实验文本执行一次向量化，不写入 Milvus。 */
@Service
public class EmbeddingPreviewService {
    private final ObjectProvider<EmbeddingModel> models;
    private final String modelName;

    public EmbeddingPreviewService(@Qualifier("doubaoEmbeddingModel") ObjectProvider<EmbeddingModel> models,
            @Value("${meetflow.ai.doubao.embedding.model:}") String modelName) {
        this.models = models;
        this.modelName = modelName;
    }

    /** 返回实际向量维度及少量预览值，文本不会写入应用日志。 */
    public EmbeddingPreviewView preview(String text) {
        if (text == null || text.isBlank() || text.length() > 1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "实验文本不能为空且不能超过 1000 字符");
        var model = models.getIfAvailable();
        if (model == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "豆包文本向量模型未启用");
        try {
            float[] vector = model.embed(text);
            if (vector == null || vector.length == 0) throw new IllegalStateException("向量结果为空");
            var sample = new ArrayList<Float>();
            for (int i = 0; i < Math.min(vector.length, 8); i++) sample.add(vector[i]);
            var result = new EmbeddingPreviewView();
            result.setModel(modelName);
            result.setDimension(vector.length);
            result.setSample(sample);
            return result;
        } catch (Exception ex) {
            ErrorDiagnostics.error("豆包文本向量化失败", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "向量化失败，请检查模型 ID、接口类型、额度和网络");
        }
    }
}

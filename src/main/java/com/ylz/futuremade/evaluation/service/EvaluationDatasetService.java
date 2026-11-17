package com.ylz.futuremade.evaluation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.evaluation.model.dto.EvaluationDataset;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.IOException;

/** 读取随应用发布的虚构会议评测集。 */
@Service
public class EvaluationDatasetService {
    private final ObjectMapper json;
    public EvaluationDatasetService(ObjectMapper json) { this.json = json; }
    /** 读取评测样本和人工预设答案。 */
    public EvaluationDataset dataset() {
        try (var input = new ClassPathResource("evaluation/meeting-cases.json").getInputStream()) {
            return json.readValue(input, EvaluationDataset.class);
        } catch (IOException ex) {
            throw new IllegalStateException("会议评测集无法读取", ex);
        }
    }
}

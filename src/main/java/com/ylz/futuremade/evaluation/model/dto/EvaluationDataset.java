package com.ylz.futuremade.evaluation.model.dto;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 评测数据集。 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvaluationDataset {
    /** 评测集版本。 */
    private String datasetVersion;
    /** 样本来源说明。 */
    private String origin;
    /** 评测样本列表。 */
    private List<EvaluationCase> cases;
}

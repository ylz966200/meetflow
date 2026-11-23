package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
/** 后台生成任务的进度，不包含会议正文或批次输入。 */
@Getter @Setter
public class GenerationTaskView {
    /** 任务唯一标识。 */ private String taskId;
    /** 任务状态码，参见 GenerationTaskStatus。 */ private Integer status;
    /** 当前有效叶子批次数。 */ private int totalBatches;
    /** 已成功的有效批次数。 */ private int succeededBatches;
    /** 失败的有效批次数。 */ private int failedBatches;
    /** 最终保存的纪要版本。 */ private Integer resultVersion;
    /** 安全错误类型。 */ private String errorCode;
    /** 较长分段未提取到内容时提示核对的序号，从一开始；空结果本身合法。 */ private List<Integer> suspiciousChunks = new ArrayList<>();
}

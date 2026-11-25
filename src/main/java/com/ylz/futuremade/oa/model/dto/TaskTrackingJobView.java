package com.ylz.futuremade.oa.model.dto;
import lombok.Getter;
import lombok.Setter;
import com.ylz.futuremade.knowledge.model.dto.MeetingResearchAnswer;
/** 可供当前发起人查询的单会议研究进度，不含执行令牌。 */
@Getter @Setter
public class TaskTrackingJobView {
    /** 子任务编号。 */ private String id;
    /** 本次追踪批号。 */ private String runId;
    /** 研究会议编号。 */ private String meetingId;
    /** 状态：1待执行、2执行中、3成功、4失败。 */ private Integer status;
    /** 失败时的安全提示。 */ private String message;
    /** 完成结果，读取时重新校验来源权限和版本。 */ private MeetingResearchAnswer result;
}

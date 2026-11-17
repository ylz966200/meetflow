package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
/** 生成会议纪要时发送给模型的输入。 */
@Getter
@Setter
public class MinutesModelInput {
    /** 用户确认的大纲标题。 */
    private List<String> confirmedOutline;
    /** 与大纲对应的会议文字稿。 */
    private String transcript;
}

package com.ylz.futuremade.oa.model.dto;

import com.ylz.futuremade.oa.model.entity.OaTask;
import lombok.Getter;
import lombok.Setter;

/** 待办展示数据及负责人账号。 */
@Getter @Setter
public class OaTaskView extends OaTask {
    /** 来源纪要所关联的大纲版本；历史来源缺失时为空。 */
    private Integer outlineVersion;
    /** 来源行动项所属的大纲序号，从1开始；无法定位时为空。 */
    private Integer outlineId;
    /** 来源纪要中保存的原始大纲标题，不随最新大纲改变。 */
    private String outlineTitle;
    /** 负责人登录名。 */ private String assigneeUsername;
}

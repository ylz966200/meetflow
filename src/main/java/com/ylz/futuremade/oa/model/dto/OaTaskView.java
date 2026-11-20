package com.ylz.futuremade.oa.model.dto;

import com.ylz.futuremade.oa.model.entity.OaTask;
import lombok.Getter;
import lombok.Setter;

/** 待办展示数据及负责人账号。 */
@Getter @Setter
public class OaTaskView extends OaTask {
    /** 负责人登录名。 */ private String assigneeUsername;
}

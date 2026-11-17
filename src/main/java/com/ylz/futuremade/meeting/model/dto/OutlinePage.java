package com.ylz.futuremade.meeting.model.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 指定大纲版本的一页标题与来源编号，不包含候选正文和文字稿。 */
@Getter @Setter
public class OutlinePage {
    /** 大纲版本号。 */ private Integer version;
    /** 关联的文字稿版本号。 */ private Integer transcriptVersion;
    /** 大纲状态码。 */ private Integer status;
    /** 文字稿是否已更新。 */ private boolean stale;
    /** 是否具有来源快照。 */ private boolean hasSources;
    /** 是否需要人工检查跨批次归并。 */ private boolean partialGrouping;
    /** 当前页码，从 1 开始。 */ private int page;
    /** 每页条目数。 */ private int size;
    /** 大纲条目总数。 */ private int total;
    /** 当前页标题。 */ private List<String> items;
    /** 当前页各标题对应的候选编号。 */ private List<List<String>> itemSourceIds;
}

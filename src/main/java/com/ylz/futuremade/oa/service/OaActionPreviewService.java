package com.ylz.futuremade.oa.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.ylz.futuremade.common.enums.BusinessStatus;
import com.ylz.futuremade.meeting.service.MinutesService;
import com.ylz.futuremade.meeting.model.dto.MinutesView;
import com.ylz.futuremade.oa.model.dto.OaActionPreview;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/** 通过 Graph 读取和检查可用于 OA 同步的会议行动项。 */
@Service
public class OaActionPreviewService {
    private final MinutesService minutes;
    private final CompiledGraph graph;

    public OaActionPreviewService(MinutesService minutes) throws Exception {
        this.minutes = minutes;
        this.graph = new StateGraph()
                .addNode("read_minutes", node_async(state -> {
                    String meetingId = state.<String>value("meetingId").orElseThrow();
                    long actor = state.<Long>value("actor").orElseThrow();
                    MinutesView view = this.minutes.get(meetingId, actor, null);
                    if (!BusinessStatus.CONFIRMED.code().equals(view.getStatus()) || Boolean.TRUE.equals(view.getStale()))
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "请先确认基于最新大纲和文字稿的纪要");
                    if (view.getStructured() == null)
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "当前纪要没有结构化行动项");
                    OaActionPreview preview = new OaActionPreview();
                    preview.setMeetingId(meetingId);
                    preview.setMinutesVersion(view.getVersion());
                    preview.setActions(view.getStructured().getActions() == null ? Collections.emptyList() : view.getStructured().getActions());
                    // Graph 的节点协议使用动态状态键，此处传递独立业务 DTO。
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("preview", preview);
                    return updates;
                }))
                .addEdge(StateGraph.START, "read_minutes")
                .addEdge("read_minutes", StateGraph.END)
                .compile();
    }

    /** 检查访问权限和纪要状态后返回行动项预览，不创建外部待办。 */
    public OaActionPreview preview(String meetingId, long actor) {
        Map<String, Object> input = new HashMap<>();
        input.put("meetingId", meetingId);
        input.put("actor", actor);
        OverAllState finalState = graph.invoke(input).orElseThrow();
        return finalState.<OaActionPreview>value("preview").orElseThrow();
    }
}

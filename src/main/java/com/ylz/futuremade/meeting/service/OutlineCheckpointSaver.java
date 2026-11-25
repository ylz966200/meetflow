package com.ylz.futuremade.meeting.service;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.meeting.mapper.AiGenerationTaskMapper;
import com.ylz.futuremade.meeting.model.dto.OutlineGraphCheckpoint;
import com.ylz.futuremade.meeting.model.dto.GenerationTaskView;
import com.ylz.futuremade.meeting.enums.GenerationProgressStage;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * 使用任务行保存大纲和纪要 Graph 的最近检查点，并用任务租约拦截过期执行者。
 */
@Component
public class OutlineCheckpointSaver implements BaseCheckpointSaver {
    static final String LEASE_TOKEN = "outlineLeaseToken";
    static final String ROUND = "mergeRound";

    private final AiGenerationTaskMapper tasks;
    private final ObjectMapper json;

    public OutlineCheckpointSaver(AiGenerationTaskMapper tasks, ObjectMapper json) {
        this.tasks = tasks;
        this.json = json;
    }

    @Override
    public Collection<Checkpoint> list(RunnableConfig config) {
        return read(config).stream().toList();
    }

    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        return read(config).filter(checkpoint ->
                config.checkPointId().map(checkpoint.getId()::equals).orElse(true));
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws JsonProcessingException {
        OutlineGraphCheckpoint snapshot = new OutlineGraphCheckpoint(checkpoint.getId(), checkpoint.getNodeId(),
                checkpoint.getNextNodeId(), (Integer) checkpoint.getState().get(ROUND));
        String token = (String) config.metadata(LEASE_TOKEN).orElseThrow();
        if (tasks.saveOutlineCheckpoint(taskId(config), token, json.writeValueAsString(snapshot)) != 1) {
            throw new IllegalStateException("生成 Graph 检查点写入时执行权已失效");
        }
        return config.withCheckPointId(checkpoint.getId());
    }

    @Override
    public Tag release(RunnableConfig config) {
        // 终态检查点保留在任务行，和任务一同管理生命周期。
        return new Tag(taskId(config), list(config));
    }

    /**
     * 将持久化检查点转换为查询展示数据；它不代表节点当前正在运行。
     */
    public void addProgress(GenerationTaskView view) {
        Optional<Checkpoint> saved = read(RunnableConfig.builder().threadId(view.getTaskId()).build());
        saved.ifPresent(checkpoint -> {
            view.setCheckpointStage(GenerationProgressStage.codeOf(checkpoint.getNodeId()));
            view.setCheckpointNextStage(GenerationProgressStage.codeOf(checkpoint.getNextNodeId()));
            view.setCheckpointRound((Integer) checkpoint.getState().get(ROUND));
        });
    }

    private Optional<Checkpoint> read(RunnableConfig config) {
        String value = tasks.outlineCheckpoint(taskId(config));
        if (value == null) return Optional.empty();
        try {
            OutlineGraphCheckpoint snapshot = json.readValue(value, OutlineGraphCheckpoint.class);
            Map<String, Object> state = new HashMap<>();
            if (snapshot.round() != null) state.put(ROUND, snapshot.round());
            return Optional.of(Checkpoint.builder().id(snapshot.id()).nodeId(snapshot.node())
                    .nextNodeId(snapshot.nextNode()).state(state).build());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("生成 Graph 检查点无法读取", exception);
        }
    }

    private String taskId(RunnableConfig config) {
        return config.threadId().orElseThrow();
    }
}

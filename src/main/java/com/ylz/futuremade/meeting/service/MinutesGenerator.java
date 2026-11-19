package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.assistant.model.dto.AiCallContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.assistant.service.AiModelGateway;
import org.springframework.stereotype.Component;

import java.util.List;
import com.ylz.futuremade.meeting.model.dto.MinutesModelInput;
import com.ylz.futuremade.meeting.model.dto.MinutesBatchResponse;
import com.ylz.futuremade.meeting.model.dto.OutlineView;
import com.ylz.futuremade.meeting.model.dto.StructuredMinutes;
import java.util.HashSet;
import java.util.Set;

/**
 * 根据文字稿和已确认大纲生成待审核纪要。
 */
@Component
public class MinutesGenerator {
    private final AiModelGateway model;
    private final ObjectMapper json;

    public MinutesGenerator(AiModelGateway model, ObjectMapper json) {
        this.model = model;
        this.json = json;
    }

    public StructuredMinutes generate(String transcript, List<String> outline, AiCallContext audit) {
        try {
            var input = new MinutesModelInput();
            input.setConfirmedOutline(outline);
            input.setTranscript(transcript);
            String data = json.writeValueAsString(input);
            var modelResponse = model.call(audit, data);
            String response = modelResponse.getResult().getOutput().getText();
            if (modelResponse.hasFinishReasons(Set.of("length", "LENGTH")))
                throw new IllegalArgumentException("模型输出被截断");
            if (response == null || response.isBlank() || response.length() > MinutesService.MAX_CONTENT_LENGTH)
                throw new IllegalArgumentException("纪要内容无效");
            var result = json.readValue(response, MinutesBatchResponse.class);
            if (result.getMinutes() == null || result.getActions() == null)
                throw new IllegalArgumentException("纪要结果格式无效");
            Set<Integer> covered = new HashSet<>();
            for (var item : result.getMinutes()) {
                if (item.getOutlineId() == null || item.getOutlineId() < 1 || item.getOutlineId() > outline.size()
                        || !covered.add(item.getOutlineId()) || !validItem(item))
                    throw new IllegalArgumentException("纪要条目无效");
                // 旧文字稿没有可验证的来源编号，不保存模型虚构的编号。
                item.setSourceSegmentIds(List.of());
                if (item.getDiscussion() != null) {
                    java.util.stream.Stream.concat(item.getDiscussion().stream(), item.getConclusions().stream())
                            .forEach(point -> point.setSourceSegmentIds(List.of()));
                }
            }
            if (covered.size() != outline.size()) throw new IllegalArgumentException("纪要未完整覆盖大纲");
            for (var action : result.getActions()) {
                if (action.getOutlineId() == null || !covered.contains(action.getOutlineId())
                        || action.getContent() == null || action.getContent().isBlank() || action.getNeedsReview() == null)
                    throw new IllegalArgumentException("行动项无效");
                action.setSourceSegmentIds(List.of());
            }
            result.getMinutes().sort(java.util.Comparator.comparing(item -> item.getOutlineId()));
            var outlineView = new OutlineView();
            outlineView.setItems(outline);
            return MinutesStructure.fromBatches(outlineView, result.getMinutes(), result.getActions());
        } catch (Exception ex) {
            throw new MinutesGenerationException(ex);
        }
    }

    private boolean validItem(com.ylz.futuremade.meeting.model.dto.MinutesBatchItem item) {
        if (item.getDiscussion() == null && item.getConclusions() == null)
            return item.getContent() != null && !item.getContent().isBlank() && item.getNeedsReview() != null;
        if (item.getDiscussion() == null || item.getConclusions() == null
                || item.getDiscussion().isEmpty() && item.getConclusions().isEmpty()) return false;
        return java.util.stream.Stream.concat(item.getDiscussion().stream(), item.getConclusions().stream())
                .allMatch(point -> point != null && point.getContent() != null && !point.getContent().isBlank() && point.getNeedsReview() != null);
    }

    /**
     * 模型调用或输出格式无效。
     */
    public static class MinutesGenerationException extends RuntimeException {
        public MinutesGenerationException(Throwable cause) {
            super(cause);
        }
    }
}

package com.ylz.futuremade.meeting.transcript;

import com.ylz.futuremade.meeting.model.dto.ChunkUtterance;
import com.ylz.futuremade.meeting.model.dto.SpeakerReview;
import com.ylz.futuremade.meeting.model.dto.TranscriptChunkView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 按完整发言顺序分段并附带上一段末尾的参考上文。
 */
@Component
public class TranscriptChunker {
    private final ChunkProperties properties;

    public TranscriptChunker(ChunkProperties properties) {
        this.properties = properties;
    }

    /**
     * 生成技术分段预览，不识别语义话题，也不调用模型。
     */
    public List<TranscriptChunkView> split(String taskId, List<TranscriptSegment> segments, SpeakerReview review) {
        int target = properties.getTargetChars(), max = properties.getMaxChars();
        if (target < 1 || max < target || properties.getContextChars() < 0 || properties.getContextUtterances() < 0)
            throw new IllegalStateException("文字分段配置无效");
        if (segments.size() != review.getAssignments().size())
            throw new IllegalStateException("说话人校对与转写片段数量不一致");
        List<TranscriptChunkView> result = new ArrayList<>();
        int i = 0;
        while (i < segments.size()) {
            List<ChunkUtterance> current = new ArrayList<>();
            int chars = 0;
            while (i < segments.size()) {
                var segment = segments.get(i);
                int length = segment.text().length();
                if (length > max)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "第 " + (i + 1) + " 条发言超过单段长度上限，请先检查该识别结果");
                // 当前分段达到目标，或下一条完整发言会超过硬上限时，留给下一段处理。
                if (!current.isEmpty() && (chars >= target || chars + length > max)) break;
                var attribution = review.getAssignments().get(i);
                String speaker = attribution.getMemberUsername() != null ? attribution.getMemberUsername() : attribution.getExternalName();
                if (speaker == null || speaker.isBlank())
                    speaker = "unknown".equals(segment.speakerLabel()) ? "未知说话人" : "说话人 " + segment.speakerLabel();
                var utterance = new ChunkUtterance();
                utterance.setTaskId(taskId);
                utterance.setSegmentIndex(i);
                utterance.setStartMillis(segment.startMillis());
                utterance.setEndMillis(segment.endMillis());
                utterance.setSpeaker(speaker);
                utterance.setText(segment.text());
                current.add(utterance);
                chars += length;
                i++;
            }
            result.add(chunk(taskId, review.getVersion(), result.size() + 1, current, result, chars));
        }
        return result;
    }

    /**
     * 组装正文并从上一段末尾选择完整发言作为参考。
     */
    private TranscriptChunkView chunk(String taskId, int version, int index, List<ChunkUtterance> body, List<TranscriptChunkView> preceding, int chars) {
        var out = new TranscriptChunkView();
        out.setTaskId(taskId);
        out.setSpeakerReviewVersion(version);
        out.setChunkIndex(index);
        out.setUtterances(List.copyOf(body));
        out.setTextChars(chars);
        List<ChunkUtterance> context = new ArrayList<>();
        int size = 0;
        if (!preceding.isEmpty()) {
            var previous = preceding.get(preceding.size() - 1).getUtterances();
            for (int i = previous.size() - 1; i >= 0 && context.size() < properties.getContextUtterances(); i--) {
                var utterance = previous.get(i);
                if (size + utterance.getText().length() > properties.getContextChars()) break;
                context.add(utterance);
                size += utterance.getText().length();
            }
        }
        Collections.reverse(context);
        out.setContextBefore(List.copyOf(context));
        return out;
    }
}

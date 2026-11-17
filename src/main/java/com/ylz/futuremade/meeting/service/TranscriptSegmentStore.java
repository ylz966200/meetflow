package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.meeting.mapper.TranscriptSegmentMapper;
import com.ylz.futuremade.meeting.model.entity.TranscriptSegmentModel;
import com.ylz.futuremade.meeting.transcript.ChunkProperties;
import com.ylz.futuremade.meeting.transcript.TranscriptSegment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 保存不可变文字稿版本的有序片段；历史版本可继续使用正文兼容。
 */
@Service
public class TranscriptSegmentStore {
    /**
     * 单次数据库插入的最大片段数。
     */
    private static final int INSERT_BATCH_SIZE = 500;
    private final TranscriptSegmentMapper mapper;
    private final ChunkProperties properties;

    public TranscriptSegmentStore(TranscriptSegmentMapper mapper, ChunkProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    /**
     * 与原大纲分段规则一致地拆分原文；不在换行处创造虚假发言。
     */
    public List<TranscriptSegment> parse(String text) {
        int max = properties.getMaxChars();
        if (max < 2) throw new IllegalStateException("分段最大长度配置过小");
        var result = new ArrayList<TranscriptSegment>();
        for (String line : text.split("\\R")) {
            if (line.isBlank()) continue;
            int offset = 0;
            while (offset < line.length()) {
                int end = Math.min(line.length(), offset + max);
                if (end < line.length() && Character.isHighSurrogate(line.charAt(end - 1)) && Character.isLowSurrogate(line.charAt(end)))
                    end--;
                String piece = line.substring(offset, end);
                if (!piece.isBlank()) result.add(new TranscriptSegment(0, 1, "unknown", piece));
                offset = end;
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("文字稿没有可分析的内容");
        return result;
    }

    /**
     * 在文字稿版本创建事务中插入片段。
     */
    public void saveVersion(String meetingId, int version, String text) {
        var segments = parse(text);
        var rows = new ArrayList<TranscriptSegmentModel>();
        for (int i = 0; i < segments.size(); i++) {
            var row = new TranscriptSegmentModel();
            row.setMeetingId(meetingId);
            row.setTranscriptVersion(version);
            row.setSequenceNo(i);
            row.setContent(segments.get(i).text());
            rows.add(row);
            if (rows.size() == INSERT_BATCH_SIZE) {
                mapper.insertMany(rows);
                rows.clear();
            }
        }
        if (!rows.isEmpty()) mapper.insertMany(rows);
    }

    /**
     * 查找指定版本的片段；空列表表示历史版本尚未迁移。
     */
    public List<TranscriptSegmentModel> find(String meetingId, int version) {
        return mapper.findByVersion(meetingId, version);
    }

    /** 读取指定版本中被引用的片段。 */
    public List<TranscriptSegmentModel> findByIndices(String meetingId, int version, List<Integer> indices) {
        return indices.isEmpty() ? List.of() : mapper.findByIndices(meetingId, version, indices);
    }
}

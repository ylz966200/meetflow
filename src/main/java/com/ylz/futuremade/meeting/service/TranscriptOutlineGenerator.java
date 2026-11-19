package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.assistant.model.dto.AiCallContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.assistant.service.ModelOutputTruncatedException;
import com.ylz.futuremade.meeting.model.dto.*;
import com.ylz.futuremade.meeting.transcript.*;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import com.ylz.futuremade.prompt.model.entity.PromptVersion;
import com.ylz.futuremade.prompt.service.PromptService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 以保存的文字稿为来源提取候选信息，再归并成大纲草稿。
 */
@Service
public class TranscriptOutlineGenerator {
    private final TranscriptChunker chunker;
    private final ChunkProperties chunks;
    private final MeetingContextProperties contextProperties;
    private final ChunkExtractionAgent agent;
    private final TopicAnalysisService grouping;
    private final PromptService prompts;
    private final ObjectMapper json;
    private final TranscriptSegmentStore segmentStore;

    public TranscriptOutlineGenerator(TranscriptChunker chunker, ChunkProperties chunks, MeetingContextProperties contextProperties,
                                      ChunkExtractionAgent agent, TopicAnalysisService grouping, PromptService prompts, ObjectMapper json, TranscriptSegmentStore segmentStore) {
        this.chunker = chunker;
        this.chunks = chunks;
        this.contextProperties = contextProperties;
        this.agent = agent;
        this.grouping = grouping;
        this.prompts = prompts;
        this.json = json;
        this.segmentStore = segmentStore;
    }

    public TranscriptOutlineDraft generate(String text, String meetingId, int transcriptVersion, long actor, String username, String requestKey) throws Exception {
        var chunks = prepareChunks(text, meetingId, transcriptVersion);
        var all = chunks.stream().flatMap(chunk -> chunk.getUtterances().stream()).toList();
        var prompt = prompts.active(PromptScenarioCode.TRANSCRIPT_EXTRACTION.code());
        var results = new ArrayList<ChunkExtractionView>();
        for (var chunk : chunks)
            results.add(extractChunk(chunk, all, prompt, meetingId, transcriptVersion, actor, username, requestKey));
        return buildDraft(results, all, transcriptVersion, meetingId, actor, requestKey, null);
    }

    /**
     * 按文字稿版本生成稳定的分段快照。
     */
    public List<TranscriptChunkView> prepareChunks(String text, String meetingId, int transcriptVersion) {
        var stored = segmentStore.find(meetingId, transcriptVersion);
        var segments = stored.isEmpty() ? segmentStore.parse(text) : stored.stream().map(row -> new TranscriptSegment(0, 1, "unknown", row.getContent())).toList();
        var review = new SpeakerReview();
        review.setVersion(transcriptVersion);
        review.setAssignments(segments.stream().map(segment -> new SpeakerAttribution()).toList());
        return chunker.split(meetingId + ":" + transcriptVersion, segments, review);
    }

    /**
     * 提取单个已固定分段，供同步和后台流程共用。
     */
    public ChunkExtractionView extractChunk(TranscriptChunkView chunk, List<ChunkUtterance> all, PromptVersion prompt,
                                            String meetingId, int version, long actor, String username, String key) throws Exception {
        var items = extract(chunk, all, prompt, meetingId, version, actor, username, key);
        var view = new ChunkExtractionView();
        view.setTaskId(chunk.getTaskId());
        view.setChunkIndex(chunk.getChunkIndex());
        view.setSpeakerReviewVersion(version);
        view.setItems(items);
        return view;
    }

    /**
     * 根据已经保存的分段结果生成大纲及来源快照。
     */
    public TranscriptOutlineDraft buildDraft(List<ChunkExtractionView> results, List<ChunkUtterance> all, int transcriptVersion,
                                             String meetingId, long actor, String requestKey, PromptVersion groupingPrompt) throws Exception {
        var grouped = groupingPrompt == null ? grouping.group(results, meetingId, actor, requestKey)
                : grouping.group(results, meetingId, actor, requestKey, groupingPrompt);
        var source = grouped.getSource();
        if (source.getGroups().isEmpty()) throw new IllegalStateException("未提取到可用于生成大纲的内容");
        var sourceSegments = new ArrayList<OutlineSourceSegment>();
        for (var item : all) {
            var segment = new OutlineSourceSegment();
            segment.setSegmentIndex(item.getSegmentIndex());
            segment.setText(item.getText());
            sourceSegments.add(segment);
        }
        source.setSourceSegments(sourceSegments);
        source.setTranscriptVersion(transcriptVersion);
        var titles = source.getGroups().stream().map(OutlineSourceGroup::getTitle).toList();
        var draft = new TranscriptOutlineDraft();
        draft.setTitles(titles);
        draft.setSource(source);
        return draft;
    }

    private List<ChunkExtractionItem> extract(TranscriptChunkView chunk, List<ChunkUtterance> all, PromptVersion prompt,
                                              String meetingId, int version, long actor, String username, String key) throws Exception {
        var audit = new AiCallContext(prompt, meetingId, version, actor, key, null);
        try {
            int first = chunk.getUtterances().get(0).getSegmentIndex();
            var context = new ChunkExtractionContext(chunk, new MeetingContextTools(all, first, contextProperties));
            var result = agent.extract(context, audit, username);
            return result.getItems();
        } catch (ModelOutputTruncatedException ex) {
            var utterances = chunk.getUtterances();
            if (utterances.size() < 2) throw ex;
            int middle = utterances.size() / 2;
            var left = part(chunk, utterances.subList(0, middle), all);
            var right = part(chunk, utterances.subList(middle, utterances.size()), all);
            var combined = new ArrayList<>(extract(left, all, prompt, meetingId, version, actor, username, key));
            combined.addAll(extract(right, all, prompt, meetingId, version, actor, username, key));
            return combined;
        }
    }

    private TranscriptChunkView part(TranscriptChunkView original, List<ChunkUtterance> utterances, List<ChunkUtterance> all) {
        var result = new TranscriptChunkView();
        result.setTaskId(original.getTaskId());
        result.setChunkIndex(original.getChunkIndex());
        result.setSpeakerReviewVersion(original.getSpeakerReviewVersion());
        result.setUtterances(List.copyOf(utterances));
        result.setTextChars(utterances.stream().mapToInt(item -> item.getText().length()).sum());
        int first = utterances.get(0).getSegmentIndex(), length = 0;
        var previous = new ArrayList<ChunkUtterance>();
        for (int i = first - 1; i >= 0 && previous.size() < chunks.getContextUtterances(); i--) {
            var item = all.get(i);
            if (length + item.getText().length() > chunks.getContextChars()) break;
            previous.add(0, item);
            length += item.getText().length();
        }
        result.setContextBefore(previous);
        return result;
    }

}

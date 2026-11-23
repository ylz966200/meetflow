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
    private final OutlineExtractionReview extractionReview;

    public TranscriptOutlineGenerator(TranscriptChunker chunker, ChunkProperties chunks, MeetingContextProperties contextProperties,
                                      ChunkExtractionAgent agent, TopicAnalysisService grouping, PromptService prompts, ObjectMapper json, TranscriptSegmentStore segmentStore,
                                      OutlineExtractionReview extractionReview) {
        this.chunker = chunker;
        this.chunks = chunks;
        this.contextProperties = contextProperties;
        this.agent = agent;
        this.grouping = grouping;
        this.prompts = prompts;
        this.json = json;
        this.segmentStore = segmentStore;
        this.extractionReview = extractionReview;
    }

    public TranscriptOutlineDraft generate(String text, String meetingId, int transcriptVersion, long actor, String username, String requestKey) throws Exception {
        List<TranscriptChunkView> preparedChunks = prepareChunks(text, meetingId, transcriptVersion);
        List<ChunkUtterance> all = preparedChunks.stream().flatMap(chunk -> chunk.getUtterances().stream()).toList();
        PromptVersion prompt = prompts.active(PromptScenarioCode.TRANSCRIPT_EXTRACTION.code());
        List<ChunkExtractionView> results = new ArrayList<>();
        for (TranscriptChunkView chunk : preparedChunks)
            results.add(extractChunk(chunk, all, prompt, meetingId, transcriptVersion, actor, username, requestKey));
        return buildDraft(results, all, transcriptVersion, meetingId, actor, requestKey, null);
    }

    /**
     * 按文字稿版本生成稳定的分段快照。
     */
    public List<TranscriptChunkView> prepareChunks(String text, String meetingId, int transcriptVersion) {
        List<com.ylz.futuremade.meeting.model.entity.TranscriptSegmentModel> stored = segmentStore.find(meetingId, transcriptVersion);
        List<TranscriptSegment> segments = stored.isEmpty() ? segmentStore.parse(text) : stored.stream().map(row -> new TranscriptSegment(0, 1, "unknown", row.getContent())).toList();
        SpeakerReview review = new SpeakerReview();
        review.setVersion(transcriptVersion);
        review.setAssignments(segments.stream().map(segment -> new SpeakerAttribution()).toList());
        return chunker.split(meetingId + ":" + transcriptVersion, segments, review);
    }

    /**
     * 提取单个已固定分段，供同步和后台流程共用。
     */
    public ChunkExtractionView extractChunk(TranscriptChunkView chunk, List<ChunkUtterance> all, PromptVersion prompt,
                                            String meetingId, int version, long actor, String username, String key) throws Exception {
        ChunkExtractionView view = extractionReview.extract(chunk.getTextChars(),
                () -> extract(chunk, all, prompt, meetingId, version, actor, username, key));
        view.setTaskId(chunk.getTaskId());
        view.setChunkIndex(chunk.getChunkIndex());
        view.setSpeakerReviewVersion(version);
        return view;
    }

    /**
     * 根据已经保存的分段结果生成大纲及来源快照。
     */
    public TranscriptOutlineDraft buildDraft(List<ChunkExtractionView> results, List<ChunkUtterance> all, int transcriptVersion,
                                             String meetingId, long actor, String requestKey, PromptVersion groupingPrompt) throws Exception {
        TopicAnalysisResult grouped = groupingPrompt == null ? grouping.group(results, meetingId, actor, requestKey)
                : grouping.group(results, meetingId, actor, requestKey, groupingPrompt);
        return buildDraft(grouped, all, transcriptVersion);
    }

    /** 将已完成的归并结果组装为大纲草稿，不重新调用模型。 */
    public TranscriptOutlineDraft buildDraft(TopicAnalysisResult grouped, List<ChunkUtterance> all, int transcriptVersion) {
        OutlineSourceSnapshot source = grouped.getSource();
        List<OutlineSourceSegment> sourceSegments = new ArrayList<>();
        for (ChunkUtterance item : all) {
            OutlineSourceSegment segment = new OutlineSourceSegment();
            segment.setSegmentIndex(item.getSegmentIndex());
            segment.setText(item.getText());
            sourceSegments.add(segment);
        }
        source.setSourceSegments(sourceSegments);
        source.setTranscriptVersion(transcriptVersion);
        List<String> titles = source.getGroups().stream().map(OutlineSourceGroup::getTitle).toList();
        TranscriptOutlineDraft draft = new TranscriptOutlineDraft();
        draft.setTitles(titles);
        draft.setSource(source);
        return draft;
    }

    private List<ChunkExtractionItem> extract(TranscriptChunkView chunk, List<ChunkUtterance> all, PromptVersion prompt,
                                              String meetingId, int version, long actor, String username, String key) throws Exception {
        AiCallContext audit = new AiCallContext(prompt, meetingId, version, actor, key, null);
        try {
            int first = chunk.getUtterances().get(0).getSegmentIndex();
            ChunkExtractionContext context = new ChunkExtractionContext(chunk, new MeetingContextTools(all, first, contextProperties));
            ChunkExtractionView result = agent.extract(context, audit, username);
            return result.getItems();
        } catch (ModelOutputTruncatedException ex) {
            List<ChunkUtterance> utterances = chunk.getUtterances();
            if (utterances.size() < 2) throw ex;
            int middle = utterances.size() / 2;
            TranscriptChunkView left = part(chunk, utterances.subList(0, middle), all);
            TranscriptChunkView right = part(chunk, utterances.subList(middle, utterances.size()), all);
            List<ChunkExtractionItem> combined = new ArrayList<>(extract(left, all, prompt, meetingId, version, actor, username, key));
            combined.addAll(extract(right, all, prompt, meetingId, version, actor, username, key));
            return combined;
        }
    }

    private TranscriptChunkView part(TranscriptChunkView original, List<ChunkUtterance> utterances, List<ChunkUtterance> all) {
        TranscriptChunkView result = new TranscriptChunkView();
        result.setTaskId(original.getTaskId());
        result.setChunkIndex(original.getChunkIndex());
        result.setSpeakerReviewVersion(original.getSpeakerReviewVersion());
        result.setUtterances(List.copyOf(utterances));
        result.setTextChars(utterances.stream().mapToInt(item -> item.getText().length()).sum());
        int first = utterances.get(0).getSegmentIndex(), length = 0;
        List<ChunkUtterance> previous = new ArrayList<>();
        for (int i = first - 1; i >= 0 && previous.size() < chunks.getContextUtterances(); i--) {
            ChunkUtterance item = all.get(i);
            if (length + item.getText().length() > chunks.getContextChars()) break;
            previous.add(0, item);
            length += item.getText().length();
        }
        result.setContextBefore(previous);
        return result;
    }

}

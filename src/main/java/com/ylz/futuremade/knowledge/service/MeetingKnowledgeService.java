package com.ylz.futuremade.knowledge.service;

import com.ylz.futuremade.assistant.model.dto.AiCallContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.assistant.service.AiModelGateway;
import com.ylz.futuremade.common.enums.BusinessStatus;
import com.ylz.futuremade.knowledge.model.dto.*;
import com.ylz.futuremade.meeting.model.dto.MinutesView;
import com.ylz.futuremade.meeting.service.MinutesService;
import com.ylz.futuremade.meeting.service.MinutesStructure;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import com.ylz.futuremade.prompt.service.PromptService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 基于已确认纪要建立单会议索引并执行权限受控问答。
 */
@Service
public class MeetingKnowledgeService {
    private final MinutesService minutes;
    private final MeetingVectorStore vectors;
    private final ObjectProvider<EmbeddingModel> embeddings;
    private final AiModelGateway model;
    private final PromptService prompts;
    private final ObjectMapper json;
    private final String modelId;
    private final ChatMemory memory;
    private final KnowledgeAnswerCacheService cache;

    public MeetingKnowledgeService(MinutesService minutes, MeetingVectorStore vectors,
                                   @Qualifier("doubaoEmbeddingModel") ObjectProvider<EmbeddingModel> embeddings, AiModelGateway model,
                                   PromptService prompts, ObjectMapper json, @Value("${meetflow.ai.doubao.embedding.model:}") String modelId, ChatMemory memory, KnowledgeAnswerCacheService cache) {
        this.minutes = minutes;
        this.vectors = vectors;
        this.embeddings = embeddings;
        this.model = model;
        this.prompts = prompts;
        this.json = json;
        this.modelId = modelId;
        this.memory = memory;
        this.cache = cache;
    }

    /**
     * 对最新已确认结构化纪要显式建立或重建索引。
     */
    public KnowledgeAnswer index(String meetingId, long actor) throws Exception {
        return index(meetingId, actor, null);
    }

    /**
     * 后台执行时固定纪要版本，避免旧任务为新版重复建立索引。
     */
    public KnowledgeAnswer index(String meetingId, long actor, Integer expectedVersion) throws Exception {
        var view = confirmed(meetingId, actor);
        if (expectedVersion != null && !expectedVersion.equals(view.getVersion()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "纪要版本已变化");
        var texts = view.getStructured().getSections().stream().map(section -> section.getTitle() + "\n" + MinutesStructure.sectionContent(section)).toList();
        if (texts.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "纪要没有可索引的条目");
        var result = embedding().embed(texts);
        if (result.size() != texts.size() || result.isEmpty()) throw new IllegalStateException("向量数量不匹配");
        int dimension = result.get(0).length;
        if (dimension == 0 || result.stream().anyMatch(item -> item.length != dimension))
            throw new IllegalStateException("向量维度不一致");
        checkUnchanged(meetingId, actor, view);
        vectors.upsert(collection(dimension), marker(dimension), meetingId, view.getVersion(), result);
        checkUnchanged(meetingId, actor, view);
        var response = new KnowledgeAnswer();
        response.setMinutesVersion(view.getVersion());
        response.setSources(List.of());
        return response;
    }

    /**
     * 只检索当前会议当前已确认版本，并在模型调用前后重新校验权限和版本。
     */
    public KnowledgeAnswer answer(String meetingId, long actor, String question, String conversationId, Integer expectedVersion) throws Exception {
        return answer(meetingId, actor, question, conversationId, expectedVersion, false);
    }

    /** 结合会话改写问题后，按权限范围查找可复用的缓存。 */
    public KnowledgeAnswer answer(String meetingId, long actor, String question, String conversationId, Integer expectedVersion, boolean bypassCache) throws Exception {
        if (question == null || question.isBlank() || question.length() > 1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "问题不能为空且不能超过 1000 字符");
        var view = confirmed(meetingId, actor);
        if (expectedVersion != null && !expectedVersion.equals(view.getVersion()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "纪要版本已变化，请重新提问");
        String memoryKey = memoryKey(meetingId, actor, view.getVersion(), conversationId);
        var context = recentTurns(memoryKey);
        var prompt = prompts.active(PromptScenarioCode.MEETING_KNOWLEDGE_QA.code());
        String requestKey = UUID.randomUUID().toString();
        String retrievalQuestion = question;
        if (!context.isEmpty()) {
            var rewritePrompt = prompts.active(PromptScenarioCode.MEETING_KNOWLEDGE_QUERY_REWRITE.code());
            var audit = new AiCallContext(rewritePrompt, meetingId, view.getTranscriptVersion(), actor, requestKey, view.getOutlineVersion());
            List<Message> history = new ArrayList<>();
            for (var turn : context) {
                history.add(new UserMessage(turn.getQuestion()));
                history.add(new AssistantMessage(turn.getAnswer()));
            }
            var transformer = CompressionQueryTransformer.builder()
                    .chatClientBuilder(model.clientBuilder(audit))
                    .promptTemplate(new PromptTemplate(rewritePrompt.getContent()))
                    .build();
            retrievalQuestion = transformer.transform(Query.builder().text(question).history(history).build()).text();
            checkUnchanged(meetingId, actor, view);
        }
        String cacheKey = cache.key(meetingId, actor, view.getVersion(), prompt.getId(), modelId, retrievalQuestion);
        if (!bypassCache) {
            var cached = cache.get(cacheKey);
            if (usable(cached, view)) return reuse(cached, meetingId, actor, view, memoryKey, question);
        }
        float[] query = embedding().embed(retrievalQuestion);
        var hits = vectors.search(collection(query.length), marker(query.length), meetingId, view.getVersion(), query, 5);
        var sources = new ArrayList<KnowledgeSource>();
        for (var hit : hits) {
            int index = ((Number) hit.get("section_index")).intValue();
            var section = view.getStructured().getSections().get(index);
            var source = new KnowledgeSource();
            source.setSectionIndex(index);
            source.setTitle(section.getTitle());
            source.setContent(MinutesStructure.sectionContent(section));
            source.setScore(hit.getScore());
            sources.add(source);
        }
        if (sources.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前纪要尚未建立索引，请先建立索引");
        checkUnchanged(meetingId, actor, view);
        var input = new KnowledgeQuestionInput();
        input.setQuestion(retrievalQuestion);
        input.setHistory(List.of());
        input.setSources(sources);
        String data = json.writeValueAsString(input);
        var audit = new AiCallContext(prompt, meetingId, view.getTranscriptVersion(), actor, requestKey, view.getOutlineVersion());
        var response = model.call(audit, data);
        String output = response.getResult().getOutput().getText();
        if (response.hasFinishReasons(Set.of("length", "LENGTH")) || output == null || output.isBlank())
            throw new IllegalStateException("问答结果为空或被截断");
        checkUnchanged(meetingId, actor, view);
        // 只记录通过权限、来源版本及输出校验的完整问答，不把参考资料写入会话记忆。
        memory.add(memoryKey, List.of(new UserMessage(question), new AssistantMessage(output.strip())));
        var result = new KnowledgeAnswer();
        result.setMinutesVersion(view.getVersion());
        result.setAnswer(output.strip());
        result.setSources(sources);
        cache.put(cacheKey, result);
        return result;
    }

    private boolean usable(KnowledgeAnswer answer, MinutesView view) {
        return answer != null && answer.getMinutesVersion() == view.getVersion()
                && answer.getAnswer() != null && !answer.getAnswer().isBlank()
                && answer.getSources() != null && !answer.getSources().isEmpty();
    }

    private KnowledgeAnswer reuse(KnowledgeAnswer answer, String meetingId, long actor, MinutesView view,
                                  String memoryKey, String question) {
        checkUnchanged(meetingId, actor, view);
        answer.setCached(true);
        memory.add(memoryKey, List.of(new UserMessage(question), new AssistantMessage(answer.getAnswer())));
        return answer;
    }

    /**
     * 调用方须先校验会议权限；键包含用户、会议和纪要版本。
     */
    public void clearMemory(String meetingId, long actor, int version, String conversationId) {
        memory.clear(memoryKey(meetingId, actor, version, conversationId));
    }

    private String memoryKey(String meetingId, long actor, int version, String conversationId) {
        try {
            if (conversationId == null || !UUID.fromString(conversationId).toString().equals(conversationId))
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话标识必须是标准 UUID");
        }
        return "knowledge:" + actor + ":" + meetingId + ":" + version + ":" + conversationId;
    }

    private List<KnowledgeConversationTurn> recentTurns(String key) {
        var messages = memory.get(key);
        var turns = new ArrayList<KnowledgeConversationTurn>();
        KnowledgeConversationTurn current = null;
        for (var message : messages) {
            if (message.getMessageType() == MessageType.USER) {
                current = new KnowledgeConversationTurn();
                current.setQuestion(message.getText());
            } else if (message.getMessageType() == MessageType.ASSISTANT && current != null) {
                String answer = message.getText();
                current.setAnswer(answer.length() > 2000 ? answer.substring(0, 2000) : answer);
                turns.add(current);
                current = null;
            }
        }
        return turns.size() <= 4 ? turns : new ArrayList<>(turns.subList(turns.size() - 4, turns.size()));
    }

    private MinutesView confirmed(String id, long actor) {
        var view = minutes.get(id, actor, null);
        if (!BusinessStatus.CONFIRMED.code().equals(view.getStatus()) || Boolean.TRUE.equals(view.getStale()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先确认基于最新大纲和文字稿的纪要");
        if (view.getStructured() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前纪要是历史纯文本，请先生成结构化纪要");
        return view;
    }

    private void checkUnchanged(String id, long actor, MinutesView expected) {
        if (!Objects.equals(confirmed(id, actor).getVersion(), expected.getVersion()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "纪要已更新，请重新建立索引");
    }

    private EmbeddingModel embedding() {
        var value = embeddings.getIfAvailable();
        if (value == null || modelId.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "豆包向量模型未配置");
        return value;
    }

    private String identity() throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(modelId.getBytes(StandardCharsets.UTF_8)), 0, 8);
    }

    private String collection(int dimension) throws Exception {
        return "meetflow_minutes_" + identity() + "_" + dimension;
    }

    private String marker(int dimension) throws Exception {
        return "meetflow-minutes-v1:" + identity() + ":" + dimension;
    }
}

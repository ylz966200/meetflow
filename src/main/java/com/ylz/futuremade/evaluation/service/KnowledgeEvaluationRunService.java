package com.ylz.futuremade.evaluation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.common.enums.BusinessStatus;
import com.ylz.futuremade.evaluation.enums.KnowledgeEvaluationStage;
import com.ylz.futuremade.evaluation.enums.ManualReviewStatus;
import com.ylz.futuremade.evaluation.mapper.KnowledgeEvaluationRunMapper;
import com.ylz.futuremade.evaluation.model.dto.*;
import com.ylz.futuremade.evaluation.model.entity.KnowledgeEvaluationRun;
import com.ylz.futuremade.knowledge.service.MultiMeetingKnowledgeAccessService;
import com.ylz.futuremade.knowledge.model.dto.*;
import com.ylz.futuremade.knowledge.service.MeetingKnowledgeService;
import com.ylz.futuremade.meeting.service.MinutesService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 固定会议版本执行问答评测，阶段性保存结果与评审。 */
@Slf4j
@Service
public class KnowledgeEvaluationRunService {
    private final EvaluationDatasetService datasets;
    private final KnowledgeEvaluationRunMapper runs;
    private final MultiMeetingKnowledgeAccessService access;
    private final MeetingKnowledgeService knowledge;
    private final MinutesService minutes;
    private final EvaluationJudgeService judge;
    private final ObjectMapper json;

    public KnowledgeEvaluationRunService(EvaluationDatasetService datasets, KnowledgeEvaluationRunMapper runs,
            MultiMeetingKnowledgeAccessService access, MeetingKnowledgeService knowledge, MinutesService minutes,
            EvaluationJudgeService judge, ObjectMapper json) {
        this.datasets = datasets;
        this.runs = runs;
        this.access = access;
        this.knowledge = knowledge;
        this.minutes = minutes;
        this.judge = judge;
        this.json = json;
    }

    /** 执行一次评测，问答成功后即使模型评审失败也保留实际结果。 */
    public KnowledgeEvaluationRun run(String caseId, long actor) throws Exception {
        var dataset = datasets.knowledgeDataset(actor);
        var sample = dataset.getCases().stream().filter(item -> item.getId().equals(caseId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "样本不存在或无权访问"));
        if (sample.getHistorySource() != null && (sample.getHistory() == null || sample.getHistory().isEmpty()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "样本依赖历史调用，请先补全对话");
        var selections = new ArrayList<KnowledgeMeetingSelection>();
        for (var key : sample.getMeetingKeys()) {
            var meeting = dataset.getMeetings().stream().filter(item -> item.getKey().equals(key)).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "样本会议配置无效"));
            var selection = new KnowledgeMeetingSelection();
            selection.setMeetingId(meeting.getMeetingId());
            selection.setMinutesVersion(meeting.getMinutesVersion());
            selections.add(selection);
        }
        var input = new MultiMeetingQuestion();
        input.setQuestion(sample.getQuestion());
        input.setConversationId(UUID.randomUUID().toString());
        input.setBypassCache(true);
        input.setMeetings(selections);
        var run = new KnowledgeEvaluationRun();
        run.setId(UUID.randomUUID().toString());
        run.setDatasetVersion(dataset.getDatasetVersion());
        run.setCaseId(caseId);
        run.setActorId(actor);
        run.setMeetingIds(String.join(",", selections.stream().map(KnowledgeMeetingSelection::getMeetingId).sorted().toList()));
        run.setStatus(BusinessStatus.RUNNING.code());
        var snapshot = new KnowledgeEvaluationSampleSnapshot();
        snapshot.setSample(sample);
        snapshot.setMeetings(dataset.getMeetings().stream().filter(item -> sample.getMeetingKeys().contains(item.getKey())).toList());
        run.setSampleJson(json.writeValueAsString(snapshot));
        runs.insert(run);
        var stage = KnowledgeEvaluationStage.ANSWER;
        try {
            if (sample.getHistory() != null && !sample.getHistory().isEmpty())
                knowledge.prepareEvaluationHistory(actor, input, sample.getHistory());
            var actual = access.answer(input, actor);
            run.setResultJson(json.writeValueAsString(actual));
            run.setRequestKey(actual.getRequestKey());
            runs.saveResult(run);
            stage = KnowledgeEvaluationStage.SOURCE_CHECK;
            var checks = sourceChecks(dataset, sample, actual);
            run.setSourceChecksJson(json.writeValueAsString(checks));
            runs.saveResult(run);
            stage = KnowledgeEvaluationStage.JUDGE;
            var judgeInput = new KnowledgeEvaluationJudgeInput();
            judgeInput.setSample(sample);
            judgeInput.setActual(actual);
            judgeInput.setSourceChecks(checks);
            var review = judge.judgeKnowledge(judgeInput, actor, run.getMeetingIds(), run.getId());
            run.setReview(review.getReview());
            run.setJudgeCallId(review.getCallId());
            run.setStatus(BusinessStatus.SUCCEEDED.code());
        } catch (Exception ex) {
            // 保存失败阶段和已完成的问答，供用户定位问题及重试评审。
            log.error("知识问答评测失败，运行ID={}，阶段={}", run.getId(), stage, ex);
            run.setStatus(BusinessStatus.FAILED.code());
            run.setErrorStage(stage.name());
            try {
                runs.finish(run);
            } catch (RuntimeException saveException) {
                ex.addSuppressed(saveException);
            }
            throw ex;
        } finally {
            try { knowledge.clearMultiple(actor, input); }
            // 清理临时会话失败不应覆盖评测主流程的结果或原始异常。
            catch (RuntimeException ex) { log.warn("评测会话清理失败，运行ID={}", run.getId(), ex); }
        }
        runs.finish(run);
        return get(run.getId(), actor);
    }

    private List<KnowledgeSourceCheck> sourceChecks(KnowledgeEvaluationDataset dataset, KnowledgeEvaluationCase sample, MultiMeetingAnswer actual) {
        var checks = new ArrayList<KnowledgeSourceCheck>();
        if (sample.getExpectedSources() == null) return checks;
        for (var expected : sample.getExpectedSources()) {
            var meeting = dataset.getMeetings().stream().filter(item -> item.getKey().equals(expected.getMeetingKey())).findFirst().orElseThrow();
            var check = new KnowledgeSourceCheck();
            check.setMeetingId(meeting.getMeetingId());
            check.setMinutesVersion(meeting.getMinutesVersion());
            check.setSectionIndex(expected.getSectionIndex());
            check.setTitle(expected.getTitle());
            check.setFound(actual.getSources().stream().anyMatch(source -> Objects.equals(source.getMeetingId(), meeting.getMeetingId())
                    && Objects.equals(source.getMinutesVersion(), meeting.getMinutesVersion())
                    && Objects.equals(source.getSectionIndex(), expected.getSectionIndex())));
            checks.add(check);
        }
        return checks;
    }

    /** 只展示本人且仍有权限访问全部会议的运行摘要。 */
    public List<KnowledgeEvaluationRun> recent(long actor) {
        return runs.recent(actor).stream().filter(run -> accessible(run, actor)).toList();
    }

    /** 校验运行归属和全部会议权限后读取记录。 */
    public KnowledgeEvaluationRun get(String id, long actor) {
        var run = runs.find(id, actor);
        if (run == null || !accessible(run, actor))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评测记录不存在或无权访问");
        return run;
    }

    /** 评审失败时重用已保存的问答，避免再次调用问答模型。 */
    public KnowledgeEvaluationRun retryReview(String id, long actor) throws Exception {
        var run = get(id, actor);
        if (runs.claimReviewRetry(id, actor) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仅评审失败的记录可以重试");
        try {
            var input = new KnowledgeEvaluationJudgeInput();
            input.setSample(json.readValue(run.getSampleJson(), KnowledgeEvaluationSampleSnapshot.class).getSample());
            input.setActual(json.readValue(run.getResultJson(), MultiMeetingAnswer.class));
            input.setSourceChecks(json.readValue(run.getSourceChecksJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<KnowledgeSourceCheck>>() {}));
            var result = judge.judgeKnowledge(input, actor, run.getMeetingIds(), run.getId());
            run.setReview(result.getReview());
            run.setJudgeCallId(result.getCallId());
            run.setStatus(BusinessStatus.SUCCEEDED.code());
            run.setErrorStage(null);
        } catch (Exception ex) {
            // 记录评审失败状态，以便保留重试入口和之前的问答结果。
            log.error("知识问答评审重试失败，运行ID={}", id, ex);
            run.setStatus(BusinessStatus.FAILED.code());
            run.setErrorStage(KnowledgeEvaluationStage.JUDGE.name());
            try {
                runs.finish(run);
            } catch (RuntimeException saveException) {
                ex.addSuppressed(saveException);
            }
            throw ex;
        }
        runs.finish(run);
        return get(id, actor);
    }

    /** 保存当前管理员对已完成问答结果的复核。 */
    public KnowledgeEvaluationRun review(String id, long actor, KnowledgeEvaluationManualReview input) {
        get(id, actor);
        if (input.getStatus() == null || (!Objects.equals(input.getStatus(), ManualReviewStatus.PASSED.code())
                && !Objects.equals(input.getStatus(), ManualReviewStatus.FAILED.code()))
                || (input.getNote() != null && input.getNote().length() > 1000))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "复核状态或备注无效");
        if (runs.review(id, actor, input.getStatus(), input.getNote()) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前记录没有可复核的问答结果");
        return get(id, actor);
    }

    private boolean accessible(KnowledgeEvaluationRun run, long actor) {
        try {
            for (String id : run.getMeetingIds().split(",")) minutes.get(id, actor, null);
            return true;
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == 403 || ex.getStatusCode().value() == 404) return false;
            throw ex;
        }
    }
}

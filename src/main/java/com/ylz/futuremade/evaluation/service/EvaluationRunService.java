package com.ylz.futuremade.evaluation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.common.enums.BusinessStatus;
import com.ylz.futuremade.evaluation.mapper.EvaluationRunMapper;
import com.ylz.futuremade.evaluation.enums.EvaluationStage;
import com.ylz.futuremade.evaluation.model.dto.EvaluationJudgeRequest;
import com.ylz.futuremade.evaluation.model.entity.EvaluationRun;
import com.ylz.futuremade.meeting.mapper.MeetingMapper;
import com.ylz.futuremade.meeting.service.OutlineService;
import com.ylz.futuremade.meeting.service.OutlineSourceService;
import com.ylz.futuremade.meeting.service.MeetingService;
import com.ylz.futuremade.meeting.service.MinutesBatchGenerator;
import com.ylz.futuremade.meeting.service.TranscriptOutlineGenerator;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import com.ylz.futuremade.prompt.service.PromptService;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.ylz.futuremade.common.logging.ErrorDiagnostics;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 使用真实会议生成组件执行评测样本并保存结果。 */

@Service
public class EvaluationRunService {
    private final EvaluationDatasetService datasets;
    private final EvaluationRunMapper runs;
    private final MeetingMapper meetings;
    private final MeetingService meetingService;
    private final TranscriptOutlineGenerator outlines;
    private final OutlineService outlineService;
    private final OutlineSourceService sources;
    private final MinutesBatchGenerator minutes;
    private final EvaluationJudgeService judge;
    private final PromptService prompts;
    private final ObjectMapper json;

    public EvaluationRunService(EvaluationDatasetService datasets, EvaluationRunMapper runs, MeetingMapper meetings,
            MeetingService meetingService, TranscriptOutlineGenerator outlines, OutlineService outlineService, OutlineSourceService sources, MinutesBatchGenerator minutes,
            EvaluationJudgeService judge, PromptService prompts, ObjectMapper json) {
        this.datasets = datasets;
        this.runs = runs;
        this.meetings = meetings;
        this.meetingService = meetingService;
        this.outlines = outlines;
        this.outlineService = outlineService;
        this.sources = sources;
        this.minutes = minutes;
        this.judge = judge;
        this.prompts = prompts;
        this.json = json;
    }

    /** 同步执行一次评测，阶段性保存结果；生成大纲仅用于本次评测，不视为人工确认。 */
    public EvaluationRun run(String caseId, long actor, String username) {
        var dataset = datasets.dataset();
        var sample = dataset.getCases().stream().filter(item -> item.getId().equals(caseId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "评测样本不存在"));
        judge.ensureAvailable();
        var run = new EvaluationRun();
        run.setId(UUID.randomUUID().toString());
        run.setDatasetVersion(dataset.getDatasetVersion());
        run.setCaseId(caseId);
        run.setActorId(actor);
        run.setMeetingId(UUID.randomUUID().toString());
        run.setStatus(BusinessStatus.RUNNING.code());
        runs.insert(run);
        EvaluationStage stage = EvaluationStage.CREATE_MEETING;
        try {
            meetings.insert(run.getMeetingId(), actor, "【评测样本】" + sample.getTitle(), null);
            String transcript = sample.getTranscript().stream().map(item -> item.getSpeaker() + "：" + item.getText()).collect(Collectors.joining("\n"));
            meetingService.saveTranscript(run.getMeetingId(), actor, transcript, 0);
            stage = EvaluationStage.OUTLINE;
            var draft = outlines.generate(transcript, run.getMeetingId(), 1, actor, username, run.getId());
            run.setOutlineJson(json.writeValueAsString(draft.getTitles()));
            runs.saveOutline(run.getId(), run.getOutlineJson(), BusinessStatus.RUNNING.code());
            var outline = outlineService.save(run.getMeetingId(), actor, 1, 0, draft.getTitles(), false);
            sources.save(run.getMeetingId(), outline.getVersion(), draft.getSource());
            outline = outlineService.latest(run.getMeetingId(), actor);
            stage = EvaluationStage.MINUTES;
            var prompt = prompts.active(PromptScenarioCode.MEETING_MINUTES.code());
            run.setMinutes(minutes.generate(outline, prompt, run.getMeetingId(), actor, username, run.getId()));
            runs.saveMinutes(run.getId(), run.getMinutes(), BusinessStatus.RUNNING.code());
            stage = EvaluationStage.JUDGE;
            var request = new EvaluationJudgeRequest();
            request.setCaseId(caseId);
            request.setActualOutput("会议大纲：\n" + String.join("\n", draft.getTitles()) + "\n\n" + run.getMinutes());
            var result = judge.judge(request, actor);
            run.setReview(result.getReview());
            run.setJudgeCallId(result.getCallId());
            run.setStatus(BusinessStatus.SUCCEEDED.code());
        } catch (Exception ex) {
            ErrorDiagnostics.error("会议评测执行失败，运行ID=" + run.getId() + "，阶段=" + stage, ex);
            run.setStatus(BusinessStatus.FAILED.code());
            run.setErrorStage(stage.name());
        }
        runs.finish(run, BusinessStatus.RUNNING.code());
        return runs.find(run.getId(), actor);
    }

    /** 获取当前管理员最近的评测运行。 */
    public List<EvaluationRun> recent(long actor) {
        return runs.recent(actor);
    }
}

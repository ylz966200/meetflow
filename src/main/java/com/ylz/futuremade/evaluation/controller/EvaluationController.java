package com.ylz.futuremade.evaluation.controller;

import com.ylz.futuremade.evaluation.model.dto.EvaluationDataset;
import com.ylz.futuremade.evaluation.model.dto.KnowledgeEvaluationDataset;
import com.ylz.futuremade.evaluation.model.dto.KnowledgeEvaluationRunRequest;
import com.ylz.futuremade.evaluation.model.dto.KnowledgeEvaluationManualReview;
import com.ylz.futuremade.evaluation.model.entity.KnowledgeEvaluationRun;
import com.ylz.futuremade.evaluation.service.KnowledgeEvaluationRunService;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.ylz.futuremade.common.model.dto.ApiError;
import com.ylz.futuremade.evaluation.model.dto.EvaluationJudgeRequest;
import com.ylz.futuremade.evaluation.model.dto.EvaluationJudgeResponse;
import com.ylz.futuremade.evaluation.model.dto.EvaluationRunRequest;
import com.ylz.futuremade.evaluation.model.entity.EvaluationRun;
import com.ylz.futuremade.evaluation.service.EvaluationRunService;
import java.util.List;
import com.ylz.futuremade.evaluation.service.EvaluationDatasetService;
import com.ylz.futuremade.evaluation.service.EvaluationJudgeService;
import com.ylz.futuremade.meeting.service.MeetingService;

import java.security.Principal;

import org.springframework.web.bind.annotation.*;

/**
 * 管理后台的会议评测数据集和模型评审接口，仅系统管理员可访问。
 */
@RestController
@RequestMapping("/api/admin/evaluation")
public class EvaluationController {
    private final EvaluationDatasetService datasets;
    private final EvaluationJudgeService judge;
    private final MeetingService meetings;
    private final EvaluationRunService runs;
    private final KnowledgeEvaluationRunService knowledgeRuns;

    public EvaluationController(EvaluationDatasetService datasets, EvaluationJudgeService judge, MeetingService meetings, EvaluationRunService runs,
                                KnowledgeEvaluationRunService knowledgeRuns) {
        this.datasets = datasets;
        this.judge = judge;
        this.meetings = meetings;
        this.runs = runs;
        this.knowledgeRuns = knowledgeRuns;
    }

    /** 向管理页面返回可读的业务错误。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> businessError(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(new ApiError(ex.getReason()));
    }

    /**
     * 查询内置虚构会议样本与预设标准答案。
     */
    @GetMapping("/dataset")
    public EvaluationDataset dataset() {
        return datasets.dataset();
    }

    /** 查询当前管理员有权查看的本地知识问答评测样本。 */
    @GetMapping("/knowledge-dataset")
    public KnowledgeEvaluationDataset knowledgeDataset(Principal principal) {
        return datasets.knowledgeDataset(meetings.userId(principal.getName()));
    }

    /** 运行有权限的固定版本问答样本，使用独立短期记忆并跳过回答缓存。 */
    @PostMapping("/knowledge-runs")
    public KnowledgeEvaluationRun runKnowledge(@RequestBody KnowledgeEvaluationRunRequest request, Principal principal) throws Exception {
        return knowledgeRuns.run(request.getCaseId(), meetings.userId(principal.getName()));
    }

    /** 查看本人且仍有会议权限的近期知识问答评测记录。 */
    @GetMapping("/knowledge-runs")
    public List<KnowledgeEvaluationRun> knowledgeRuns(Principal principal) {
        return knowledgeRuns.recent(meetings.userId(principal.getName()));
    }

    /** 查看本人且仍有会议权限的评测结果快照。 */
    @GetMapping("/knowledge-runs/{id}")
    public KnowledgeEvaluationRun knowledgeRun(@PathVariable String id, Principal principal) {
        return knowledgeRuns.get(id, meetings.userId(principal.getName()));
    }

    /** 对问答已保存但评审失败的记录重新执行评审。 */
    @PostMapping("/knowledge-runs/{id}/retry-review")
    public KnowledgeEvaluationRun retryKnowledgeReview(@PathVariable String id, Principal principal) throws Exception {
        return knowledgeRuns.retryReview(id, meetings.userId(principal.getName()));
    }

    /** 记录管理员对知识问答结果的人工复核。 */
    @PostMapping("/knowledge-runs/{id}/manual-review")
    public KnowledgeEvaluationRun manualKnowledgeReview(@PathVariable String id, @RequestBody KnowledgeEvaluationManualReview input, Principal principal) {
        return knowledgeRuns.review(id, meetings.userId(principal.getName()), input);
    }

    /**
     * 以指定模型评审给定样本的实际输出。
     */
    @PostMapping("/judge")
    public EvaluationJudgeResponse judge(@RequestBody EvaluationJudgeRequest request, Principal principal) {
        return judge.judge(request, meetings.userId(principal.getName()));
    }
    /** 使用当前管理员身份运行一条评测样本，返回执行结果。 */
    @PostMapping("/runs")
    public EvaluationRun run(@RequestBody EvaluationRunRequest request, Principal principal) {
        return runs.run(request.getCaseId(), meetings.userId(principal.getName()), principal.getName());
    }

    /** 查询当前管理员最近的评测记录。 */
    @GetMapping("/runs")
    public List<EvaluationRun> runs(Principal principal) {
        return runs.recent(meetings.userId(principal.getName()));
    }}

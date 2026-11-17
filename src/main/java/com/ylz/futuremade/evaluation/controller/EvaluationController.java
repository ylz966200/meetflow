package com.ylz.futuremade.evaluation.controller;

import com.ylz.futuremade.evaluation.model.dto.EvaluationDataset;
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

    public EvaluationController(EvaluationDatasetService datasets, EvaluationJudgeService judge, MeetingService meetings, EvaluationRunService runs) {
        this.datasets = datasets;
        this.judge = judge;
        this.meetings = meetings;
        this.runs = runs;
    }

    /**
     * 查询内置虚构会议样本与预设标准答案。
     */
    @GetMapping("/dataset")
    public EvaluationDataset dataset() {
        return datasets.dataset();
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

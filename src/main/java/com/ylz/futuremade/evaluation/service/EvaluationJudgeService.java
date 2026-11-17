package com.ylz.futuremade.evaluation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.assistant.service.AiModelGateway;
import com.ylz.futuremade.common.enums.BusinessStatus;
import com.ylz.futuremade.evaluation.model.dto.EvaluationJudgeInput;
import com.ylz.futuremade.evaluation.model.dto.EvaluationJudgeRequest;
import com.ylz.futuremade.evaluation.model.dto.EvaluationJudgeResponse;
import com.ylz.futuremade.prompt.service.PromptService;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 使用指定评审模型对人工提供的会议结果进行辅助评审。
 */
@Service
public class EvaluationJudgeService {
    private final EvaluationDatasetService datasets;
    private final AiModelGateway models;
    private final PromptService prompts;
    private final ObjectMapper json;
    private final String judgeModel;

    public EvaluationJudgeService(EvaluationDatasetService datasets, AiModelGateway models, PromptService prompts,
                                  ObjectMapper json, @Value("${meetflow.evaluation.judge-model:doubaoChatModel}") String judgeModel) {
        this.datasets = datasets;
        this.models = models;
        this.prompts = prompts;
        this.json = json;
        this.judgeModel = judgeModel;
    }

    /**
     * 按样本标准调用评审模型，保存提示词版本、输入、输出和用量。
     */
    public EvaluationJudgeResponse judge(EvaluationJudgeRequest request, long actor) {
        if (request.getActualOutput() == null || request.getActualOutput().isBlank() || request.getActualOutput().length() > 20000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入待评审内容，长度不超过 20000 字符");
        var sample = datasets.dataset().getCases().stream().filter(item -> item.getId().equals(request.getCaseId()))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "评测样本不存在"));
        var prompt = ensureAvailable();

        long callId = prompts.startCall(prompt, null, null, actor, null, null);
        long started = System.currentTimeMillis();
        try {
            var input = new EvaluationJudgeInput();
            input.setSample(sample);
            input.setActualOutput(request.getActualOutput());
            String content = json.writeValueAsString(input);
            prompts.recordInput(callId, prompt.getContent(), content);
            var result = models.call(judgeModel, prompt.getContent(), content);
            String output = result.getResult().getOutput().getText();
            prompts.recordOutput(callId, output);
            prompts.recordMetadata(callId, result, "火山方舟");
            if (output == null || output.isBlank() || result.hasFinishReasons(Set.of("length", "LENGTH")))
                throw new IllegalStateException("评审结果为空或被截断");
            prompts.finishCall(callId, BusinessStatus.SUCCEEDED.code(), System.currentTimeMillis() - started, null);
            var response = new EvaluationJudgeResponse();
            response.setCallId(callId);
            response.setReview(output);
            return response;
        } catch (Exception ex) {
            prompts.finishCall(callId, BusinessStatus.FAILED.code(), System.currentTimeMillis() - started, "EVALUATION_FAILED");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "豆包评审失败，请查看调用记录", ex);
        }
    }
    /** 在执行可能产生费用的生成流程前检查评审模型与提示词配置。 */
    public com.ylz.futuremade.prompt.model.entity.PromptVersion ensureAvailable() {
        try {
            models.model(judgeModel);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "评审模型未启用，请配置豆包模型", ex);
        }
        return prompts.active(PromptScenarioCode.EVALUATION_JUDGE.code());
    }}


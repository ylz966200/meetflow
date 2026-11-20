package com.ylz.futuremade.evaluation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.evaluation.model.dto.EvaluationDataset;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.ylz.futuremade.evaluation.model.dto.KnowledgeEvaluationDataset;
import com.ylz.futuremade.meeting.service.MinutesService;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import java.util.stream.Collectors;

/** 读取随应用发布的虚构会议评测集。 */
@Service
public class EvaluationDatasetService {
    private final ObjectMapper json;
    private final MinutesService minutes;
    private final Path knowledgeDatasetPath;
    public EvaluationDatasetService(ObjectMapper json, MinutesService minutes,
            @Value("${meetflow.evaluation.knowledge-dataset:evaluation/meeting-knowledge-qa-local.json}") String path) {
        this.json = json;
        this.minutes = minutes;
        this.knowledgeDatasetPath = Path.of(path);
    }
    /** 读取评测样本和人工预设答案。 */
    public EvaluationDataset dataset() {
        try (var input = new ClassPathResource("evaluation/meeting-cases.json").getInputStream()) {
            return json.readValue(input, EvaluationDataset.class);
        } catch (IOException ex) {
            throw new IllegalStateException("会议评测集无法读取", ex);
        }
    }

    /** 只返回当前管理员有权查看全部来源会议的本地问答样本。 */
    public KnowledgeEvaluationDataset knowledgeDataset(long actor) {
        try (var input = Files.newInputStream(knowledgeDatasetPath)) {
            var dataset = json.readValue(input, KnowledgeEvaluationDataset.class);
            var allowed = dataset.getMeetings().stream().filter(meeting -> {
                try {
                    minutes.get(meeting.getMeetingId(), actor, meeting.getMinutesVersion());
                    return true;
                } catch (ResponseStatusException ex) {
                    if (ex.getStatusCode().value() == 403 || ex.getStatusCode().value() == 404) return false;
                    throw ex;
                }
            }).toList();
            var keys = allowed.stream().map(meeting -> meeting.getKey()).collect(Collectors.toSet());
            dataset.setMeetings(allowed);
            dataset.setCases(dataset.getCases().stream()
                    .filter(item -> item.getMeetingKeys() != null && !item.getMeetingKeys().isEmpty() && keys.containsAll(item.getMeetingKeys())).toList());
            return dataset;
        } catch (java.nio.file.NoSuchFileException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "本地知识问答评测集文件不存在");
        } catch (IOException ex) {
            throw new IllegalStateException("知识问答评测集无法读取", ex);
        }
    }
}

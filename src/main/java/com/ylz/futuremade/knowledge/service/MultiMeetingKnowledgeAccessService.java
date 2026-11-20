package com.ylz.futuremade.knowledge.service;

import com.ylz.futuremade.knowledge.model.dto.MultiMeetingAnswer;
import com.ylz.futuremade.knowledge.model.dto.MultiMeetingQuestion;
import com.ylz.futuremade.meeting.enums.GenerationTaskStatus;
import com.ylz.futuremade.meeting.service.MeetingService;
import com.ylz.futuremade.meeting.service.MinutesService;
import java.util.HashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 多会议问答的权限、索引和版本校验入口。 */
@Service
public class MultiMeetingKnowledgeAccessService {
    private final MeetingKnowledgeService knowledge;
    private final MeetingService meetings;
    private final MinutesService minutes;
    private final KnowledgeIndexTaskService tasks;
    private final int maxMeetings;

    public MultiMeetingKnowledgeAccessService(MeetingKnowledgeService knowledge, MeetingService meetings,
            MinutesService minutes, KnowledgeIndexTaskService tasks,
            @Value("${meetflow.knowledge.max-meetings:5}") int maxMeetings) {
        this.knowledge = knowledge;
        this.meetings = meetings;
        this.minutes = minutes;
        this.tasks = tasks;
        this.maxMeetings = maxMeetings;
    }

    /** 全部会议通过权限及索引校验后执行问答。 */
    public MultiMeetingAnswer answer(MultiMeetingQuestion input, long actor) throws Exception {
        if (input.getMeetings() == null || input.getMeetings().isEmpty() || input.getMeetings().size() > maxMeetings)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择 1 至 " + maxMeetings + " 场会议");
        var titles = new HashMap<String, String>();
        for (var item : input.getMeetings()) {
            if (item == null || item.getMeetingId() == null || item.getMeetingId().isBlank()
                    || item.getMeetingId().contains(",") || titles.containsKey(item.getMeetingId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会议 ID 无效或重复");
            String id = item.getMeetingId();
            var view = minutes.get(id, actor, null);
            var task = tasks.latest(id);
            if (task == null || tasks.version(task) != view.getVersion() || task.getStatus() != GenerationTaskStatus.SUCCEEDED.code())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "所选会议的最新纪要索引尚未完成");
            if (item.getMinutesVersion() == null) item.setMinutesVersion(view.getVersion());
            titles.put(id, meetings.title(id, actor));
        }
        // 会议 ID 以逗号分隔保存，长度受调用记录字段限制。
        if (String.join(",", titles.keySet()).length() > 1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会议范围过长");
        return knowledge.answerMultiple(actor, input, titles);
    }
}

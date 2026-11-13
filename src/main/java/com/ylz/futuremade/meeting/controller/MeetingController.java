package com.ylz.futuremade.meeting.controller;

import com.ylz.futuremade.meeting.mapper.MeetingMapper;
import com.ylz.futuremade.meeting.service.MeetingService;
import com.ylz.futuremade.meeting.service.TranscriptAiDraftService;
import com.ylz.futuremade.meeting.model.dto.TranscriptSourceView;


import com.ylz.futuremade.meeting.model.dto.AddMemberRequest;
import com.ylz.futuremade.meeting.model.dto.ChangeMemberRequest;
import com.ylz.futuremade.meeting.model.dto.SaveTranscriptRequest;
import com.ylz.futuremade.meeting.model.dto.CreateMeetingRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 会议管理控制器：提供会议创建与查询、成员管理及会议文字稿版本读写接口。
 */
@RestController
@RequestMapping("/api/meetings")
public class MeetingController {
    private final MeetingMapper meetings;
    private final MeetingService service;
    private final TranscriptAiDraftService transcriptDrafts;
    public MeetingController(MeetingMapper meetings, MeetingService service, TranscriptAiDraftService transcriptDrafts) { this.meetings = meetings; this.service = service; this.transcriptDrafts=transcriptDrafts; }

    /** 创建会议，并将当前用户设为组织者。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeetingMapper.Meeting create(@RequestBody CreateMeetingRequest input, Principal principal) {
        String title = input.getTitle() == null ? "" : input.getTitle().strip();
        if (title.isBlank() || title.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会议主题须为 1 至 200 个字符");
        long owner = service.userId(principal.getName());
        String id = UUID.randomUUID().toString();
        meetings.insert(id, owner, title, input.getMeetingTime());
        return meetings.find(id, owner);
    }

    /** 分页查询当前用户可访问的会议。 */
    @GetMapping
    public List<MeetingMapper.Meeting> list(Principal principal, @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效");
        return meetings.list(service.userId(principal.getName()), size, (page - 1) * size);
    }

    /** 查询当前用户可访问的会议详情。 */
    @GetMapping("/{id}")
    public MeetingMapper.Meeting get(@PathVariable String id, Principal principal) {
        var meeting = meetings.find(id, service.userId(principal.getName()));
        if (meeting == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会议不存在或无权访问");
        return meeting;
    }

    /** 由会议组织者添加会议成员。 */
    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public void addMember(@PathVariable String id, @RequestBody AddMemberRequest input, Principal principal) {
        service.addMember(id, service.userId(principal.getName()), input.getUsername(), input.getRole());
    }
    /** 查询会议成员及其会议角色。 */
    @GetMapping("/{id}/members")
    public List<MeetingMapper.Member> members(@PathVariable String id, Principal principal) {
        return service.members(id, service.userId(principal.getName()));
    }
    /** 由组织者修改成员角色，并校验原角色以防并发覆盖。 */
    @PutMapping("/{id}/members/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateMember(@PathVariable String id, @PathVariable String username, @RequestBody ChangeMemberRequest input, Principal principal) {
        service.updateMember(id, service.userId(principal.getName()), username, input.getExpectedRole(), input.getNewRole());
    }
    /** 由组织者移除成员，并校验原角色以防并发覆盖。 */
    @DeleteMapping("/{id}/members/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable String id, @PathVariable String username, @RequestParam String expectedRole, Principal principal) {
        service.removeMember(id, service.userId(principal.getName()), username, expectedRole);
    }
    /** 由组织者搜索可添加到会议的系统用户。 */
    @GetMapping("/{id}/eligible-users")
    public List<String> eligibleUsers(@PathVariable String id, @RequestParam String query, Principal principal) {
        return service.searchUsers(id, service.userId(principal.getName()), query);
    }
    /** 查询会议文字稿的最新版本或指定历史版本。 */
    @GetMapping("/{id}/transcript")
    public MeetingMapper.Transcript transcript(@PathVariable String id, @RequestParam(required = false) Integer version, Principal principal) {
        return service.getTranscript(id, service.userId(principal.getName()), version);
    }
    /** 查询已保存文字稿版本的 AI 来源快照，会议成员可访问。 */
    @GetMapping("/{id}/transcript/source")
    public TranscriptSourceView transcriptSource(@PathVariable String id, @RequestParam int version, Principal principal) {
        return transcriptDrafts.sources(id, service.userId(principal.getName()), version);
    }
    /** 保存会议文字稿的新版本，并校验预期版本。 */
    @PostMapping("/{id}/transcript")
    public MeetingMapper.Transcript saveTranscript(@PathVariable String id, @RequestBody SaveTranscriptRequest input, Principal principal) {
        return service.saveTranscript(id, service.userId(principal.getName()), input.getContent(), input.getExpectedVersion(), input.getAiDraftId());
    }
}

package com.ylz.futuremade.prompt.mapper;

import com.ylz.futuremade.prompt.model.entity.PromptCall;
import com.ylz.futuremade.prompt.model.entity.PromptRelease;
import com.ylz.futuremade.prompt.model.entity.PromptScenario;
import com.ylz.futuremade.prompt.model.entity.PromptVersion;
import com.ylz.futuremade.prompt.model.entity.AiCallContent;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PromptMapper {
    @Select("SELECT code,name,active_version_id AS activeVersionId FROM mf_prompt_scenario ORDER BY code")
    List<PromptScenario> scenarios();

    @Select("SELECT code,name,active_version_id AS activeVersionId FROM mf_prompt_scenario WHERE code=#{code} FOR UPDATE")
    PromptScenario lockScenario(String code);

    @Select("SELECT id,scenario,version,content,change_note AS changeNote,created_by AS createdBy,created_at AS createdAt FROM mf_prompt_version WHERE scenario=#{scenario} ORDER BY version DESC")
    List<PromptVersion> versions(String scenario);

    @Select("SELECT id,scenario,version,content,change_note AS changeNote,created_by AS createdBy,created_at AS createdAt FROM mf_prompt_version WHERE id=#{id} AND scenario=#{scenario}")
    PromptVersion version(@Param("scenario") String scenario, @Param("id") long id);

    @Select("SELECT v.id,v.scenario,v.version,v.content,v.change_note AS changeNote,v.created_by AS createdBy,v.created_at AS createdAt FROM mf_prompt_scenario s JOIN mf_prompt_version v ON v.id=s.active_version_id AND v.scenario=s.code WHERE s.code=#{scenario}")
    PromptVersion active(String scenario);

    @Select("SELECT COALESCE(MAX(version),0) FROM mf_prompt_version WHERE scenario=#{scenario}")
    int latestVersion(String scenario);

    @Insert("INSERT INTO mf_prompt_version(scenario,version,content,change_note,created_by) VALUES(#{scenario},#{version},#{content},#{changeNote},#{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertVersion(PromptVersion version);

    @Update("UPDATE mf_prompt_scenario SET active_version_id=#{versionId} WHERE code=#{scenario}")
    int publish(@Param("scenario") String scenario, @Param("versionId") long versionId);

    @Insert("INSERT INTO mf_prompt_release_audit(scenario,old_version_id,new_version_id,actor_id,reason) VALUES(#{scenario},#{oldId},#{newId},#{actor},#{reason})")
    int releaseAudit(@Param("scenario") String scenario, @Param("oldId") Long oldId, @Param("newId") long newId, @Param("actor") long actor, @Param("reason") String reason);

    @Select("SELECT id,scenario,old_version_id AS oldVersionId,new_version_id AS newVersionId,actor_id AS actorId,reason,created_at AS createdAt FROM mf_prompt_release_audit WHERE scenario=#{scenario} ORDER BY id DESC LIMIT 100")
    List<PromptRelease> releases(String scenario);

    @Insert("INSERT INTO mf_ai_call(scenario,prompt_version_id,meeting_id,transcript_version,actor_id,status,request_key,outline_version) VALUES(#{scenario},#{promptVersionId},#{meetingId},#{transcriptVersion},#{actorId},#{status},#{requestKey},#{outlineVersion})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertCall(PromptCall call);

    /** 写入跨会议调用的会议范围。 */
    @Update("UPDATE mf_ai_call SET meeting_ids=#{meetingIds} WHERE id=#{id}")
    int updateMeetingIds(@Param("id") long id, @Param("meetingIds") String meetingIds);

    @Update("UPDATE mf_ai_call SET status=#{status},duration_ms=#{durationMs},error_category=#{errorCategory} WHERE id=#{id} AND status=3")
    int finishCall(PromptCall call);

    @Update("UPDATE mf_ai_call SET provider=#{provider},model_name=#{modelName},provider_request_id=#{providerRequestId},input_tokens=#{inputTokens},output_tokens=#{outputTokens},cached_tokens=#{cachedTokens} WHERE id=#{id}")
    int updateMetadata(PromptCall call);

    @Insert("INSERT INTO mf_ai_call_content(call_id,system_content,user_content) VALUES(#{callId},#{systemContent},#{userContent})")
    int insertContent(AiCallContent content);

    @Update("UPDATE mf_ai_call_content SET output_content=#{outputContent} WHERE call_id=#{callId}")
    int updateOutput(@Param("callId") long callId, @Param("outputContent") String outputContent);

    /**
     * 保存上下文工具的敏感调用轨迹，不写入应用日志。
     */
    @Update("UPDATE mf_ai_call_content SET tool_trace=#{trace} WHERE call_id=#{callId}")
    int updateToolTrace(@Param("callId") long callId, @Param("trace") String trace);

    @Select("SELECT call_id AS callId,system_content AS systemContent,user_content AS userContent,output_content AS outputContent,tool_trace AS toolTrace,created_at AS createdAt FROM mf_ai_call_content WHERE call_id=#{callId}")
    AiCallContent content(long callId);

    // 后台模型请求以批次 ID 关联任务，同步请求和历史记录沿用业务请求标识。
    @Select("""
            SELECT c.id, c.scenario, c.prompt_version_id AS promptVersionId, c.meeting_id AS meetingId, c.meeting_ids AS meetingIds,
                   c.transcript_version AS transcriptVersion, c.actor_id AS actorId, c.status, c.duration_ms AS durationMs,
                   c.error_category AS errorCategory, c.created_at AS createdAt, COALESCE(t.request_key, c.request_key) AS requestKey,
                   b.id AS batchId, c.outline_version AS outlineVersion, c.provider, c.model_name AS modelName,
                   c.provider_request_id AS providerRequestId, c.input_tokens AS inputTokens, c.output_tokens AS outputTokens,
                   c.cached_tokens AS cachedTokens, r.status AS businessStatus, COALESCE(r.result_version,t.result_version) AS resultVersion
            FROM mf_ai_call c
            LEFT JOIN mf_ai_generation_batch b ON b.id=c.request_key
            LEFT JOIN mf_ai_generation_task t ON t.id=b.task_id
            LEFT JOIN mf_generation_request r ON r.request_key=COALESCE(t.request_key,c.request_key)
            ORDER BY c.id DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<PromptCall> calls(@Param("limit") int limit, @Param("offset") int offset);
}

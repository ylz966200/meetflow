package com.ylz.futuremade.meeting.mapper;

import com.ylz.futuremade.meeting.model.entity.TranscriptSegmentModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 文字稿版本片段的数据访问。
 */
@Mapper
public interface TranscriptSegmentMapper {
    /**
     * 批量保存同一版本片段，调用方控制每批数量。
     */
    @Insert({"<script>", "INSERT INTO mf_transcript_segment(meeting_id,transcript_version,sequence_no,content) VALUES",
            "<foreach collection='segments' item='s' separator=','>", "(#{s.meetingId},#{s.transcriptVersion},#{s.sequenceNo},#{s.content})", "</foreach>", "</script>"})
    int insertMany(@Param("segments") List<TranscriptSegmentModel> segments);

    /**
     * 查询指定版本的全部片段，避免跨会议和跨版本引用。
     */
    @Select("SELECT id,meeting_id AS meetingId,transcript_version AS transcriptVersion,sequence_no AS sequenceNo,content,created_at AS createdAt FROM mf_transcript_segment WHERE meeting_id=#{meetingId} AND transcript_version=#{version} ORDER BY sequence_no")
    List<TranscriptSegmentModel> findByVersion(@Param("meetingId") String meetingId, @Param("version") int version);

    /** 查询当前会议指定版本中被引用的片段。 */
    @Select({"<script>", "SELECT id,meeting_id AS meetingId,transcript_version AS transcriptVersion,sequence_no AS sequenceNo,content,created_at AS createdAt FROM mf_transcript_segment WHERE meeting_id=#{meetingId} AND transcript_version=#{version} AND sequence_no IN", "<foreach collection='indices' item='index' open='(' separator=',' close=')'>#{index}</foreach>", "ORDER BY sequence_no", "</script>"})
    List<TranscriptSegmentModel> findByIndices(@Param("meetingId") String meetingId, @Param("version") int version, @Param("indices") List<Integer> indices);
}

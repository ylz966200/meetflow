package com.ylz.futuremade.meeting.transcript;
import com.ylz.futuremade.meeting.model.dto.SpeakerAttribution;
import com.ylz.futuremade.meeting.model.dto.SpeakerReview;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class TranscriptChunkerTest {
    @Test void handlesEmptyAndHardLimitWithoutLosingLastChunk() {
        var p=new ChunkProperties();p.setTargetChars(4);p.setMaxChars(6);
        var review=new SpeakerReview();review.setVersion(0);review.setAssignments(List.of());
        var chunker=new TranscriptChunker(p);
        assertTrue(chunker.split("task",List.of(),review).isEmpty());
        review.setAssignments(List.of(new SpeakerAttribution(),new SpeakerAttribution()));
        var chunks=chunker.split("task",List.of(new TranscriptSegment(0,10,"1","甲乙丙"),new TranscriptSegment(10,20,"2","丁戊己庚")),review);
        assertEquals(2,chunks.size());assertEquals("甲乙丙",chunks.get(0).getUtterances().get(0).getText());
        assertEquals("丁戊己庚",chunks.get(1).getUtterances().get(0).getText());
    }
    @Test void preservesWholeUtterancesAndContextInOrder() {
        var p=new ChunkProperties();p.setTargetChars(4);p.setMaxChars(6);p.setContextChars(6);p.setContextUtterances(2);
        var review=new SpeakerReview();review.setVersion(2);review.setAssignments(List.of(new SpeakerAttribution(),new SpeakerAttribution(),new SpeakerAttribution()));
        var segments=List.of(new TranscriptSegment(0,100,"1","甲乙"),new TranscriptSegment(50,200,"2","丙丁"),new TranscriptSegment(200,300,"1","戊己"));
        var chunks=new TranscriptChunker(p).split("task-a",segments,review);
        assertEquals(2,chunks.size());assertEquals("甲乙",chunks.get(0).getUtterances().get(0).getText());
        assertEquals("戊己",chunks.get(1).getUtterances().get(0).getText());assertEquals(2,chunks.get(1).getContextBefore().size());
        assertEquals("甲乙",chunks.get(1).getContextBefore().get(0).getText());assertEquals(2,chunks.get(1).getUtterances().get(0).getSegmentIndex());
    }
    @Test void rejectsSingleOversizedUtteranceWithoutTruncatingIt() {
        var p=new ChunkProperties();p.setTargetChars(3);p.setMaxChars(4);
        var review=new SpeakerReview();review.setVersion(0);review.setAssignments(List.of(new SpeakerAttribution()));
        var error=assertThrows(ResponseStatusException.class,()->new TranscriptChunker(p).split("task",List.of(new TranscriptSegment(0,100,"1","甲乙丙丁戊")),review));
        assertTrue(error.getReason().contains("第 1 条发言"));
    }
    @Test void doesNotTruncateContextWhenLastUtteranceExceedsBudget() {
        var p=new ChunkProperties();p.setTargetChars(4);p.setMaxChars(8);p.setContextChars(3);p.setContextUtterances(1);
        var review=new SpeakerReview();review.setVersion(0);review.setAssignments(List.of(new SpeakerAttribution(),new SpeakerAttribution()));
        var chunks=new TranscriptChunker(p).split("task",List.of(new TranscriptSegment(0,10,"1","一二三四五"),new TranscriptSegment(10,20,"2","下句")),review);
        assertTrue(chunks.get(1).getContextBefore().isEmpty());
    }
}

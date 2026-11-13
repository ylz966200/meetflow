package com.ylz.futuremade.meeting.transcript;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TranscriptSegmentTest {
    @Test
    void preservesUnknownSpeakerUntilUserConfirms() {
        TranscriptSegment segment = new TranscriptSegment(1000, 2300, "speaker_1", "我会整理采购清单");
        SpeakerAssignments assignments = new SpeakerAssignments();
        assertTrue(assignments.confirmedName(segment.speakerLabel()).isEmpty());
        assignments.confirm(segment.speakerLabel(), "张明");
        assertEquals("张明", assignments.confirmedName(segment.speakerLabel()).orElseThrow());
    }

    @Test
    void rejectsInvalidTimeRangesAndEmptyIdentities() {
        assertThrows(IllegalArgumentException.class, () -> new TranscriptSegment(10, 10, "speaker_1", "内容"));
        assertThrows(IllegalArgumentException.class, () -> new SpeakerAssignments().confirm("speaker_1", " "));
    }
}

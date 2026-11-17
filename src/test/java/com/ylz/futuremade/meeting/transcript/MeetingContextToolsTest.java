package com.ylz.futuremade.meeting.transcript;

import com.ylz.futuremade.meeting.model.dto.ChunkUtterance;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MeetingContextToolsTest {
    private ChunkUtterance utterance(int index, String text) {
        var item = new ChunkUtterance();
        item.setTaskId("task-a"); item.setSegmentIndex(index); item.setSpeaker("说话人"); item.setText(text);
        return item;
    }

    @Test void searchOnlyReturnsEarlierMatchingUtterancesAndPreservesOrder() {
        var properties = new MeetingContextProperties();
        var tools = new MeetingContextTools(List.of(utterance(0,"预算十万"),utterance(1,"场地"),utterance(2,"预算调整")), 2, properties);
        var result = tools.search("预算");
        assertEquals(1, result.getUtterances().size());
        assertEquals(0, result.getUtterances().get(0).getSegmentIndex());
        assertEquals(2, ToolCallbacks.from(tools).length);
    }

    @Test void continuesCallsAndLimitsReturnedItemsWithoutCuttingSpeech() {
        var properties = new MeetingContextProperties();
        properties.setMaxCalls(2); properties.setMaxUtterances(1); properties.setMaxChars(30);
        var tools = new MeetingContextTools(List.of(utterance(0,"第一句"),utterance(1,"第二句"),utterance(2,"第三句")), 3, properties);
        var result = tools.previous(3);
        assertTrue(result.isTruncated());
        assertEquals("第三句", result.getUtterances().get(0).getText());
        assertTrue(tools.search("不存在").getUtterances().isEmpty());
        assertEquals("第三句", tools.previous(3).getUtterances().get(0).getText());
    }

    @Test void oversizedSingleResultIsReportedInsteadOfSilentlyTruncated() {
        var properties = new MeetingContextProperties(); properties.setMaxChars(2);
        var tools = new MeetingContextTools(List.of(utterance(0,"预算十万")), 1, properties);
        var result = tools.search("预算");
        assertTrue(result.isTruncated());
        assertTrue(result.getUtterances().isEmpty());
    }
}

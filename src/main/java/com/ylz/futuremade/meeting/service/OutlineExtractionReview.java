package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.meeting.model.dto.ChunkExtractionItem;
import com.ylz.futuremade.meeting.model.dto.ChunkExtractionView;
import java.util.List;
import java.util.concurrent.Callable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 编排单分段提取及结果提示；空结果是合法结果，不阻断流程。 */
@Component
public class OutlineExtractionReview {
    private final int reviewEmptyChars;

    public OutlineExtractionReview(@Value("${meetflow.transcript-chunk.suspicious-empty-chars:2000}") int reviewEmptyChars) {
        if (reviewEmptyChars < 1) throw new IllegalArgumentException("空结果提示字符阈值必须大于零");
        this.reviewEmptyChars = reviewEmptyChars;
    }

    /** 执行一次模型提取；只有较长的空结果需要提示用户核对。 */
    public ChunkExtractionView extract(int textChars, Callable<List<ChunkExtractionItem>> extractor) throws Exception {
        List<ChunkExtractionItem> items = extractor.call();
        ChunkExtractionView result = new ChunkExtractionView();
        result.setItems(items);
        result.setEmptyNeedsReview(items.isEmpty() && textChars >= reviewEmptyChars);
        return result;
    }
}

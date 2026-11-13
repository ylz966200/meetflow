package com.ylz.futuremade.meeting.transcript;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 保存一份文字稿中用户确认的姓名映射；当前仅保存在内存中。 */
public final class SpeakerAssignments {
    private final Map<String, String> confirmedNames = new HashMap<>();

    public void confirm(String speakerLabel, String name) {
        if (speakerLabel == null || speakerLabel.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Speaker label and name are required");
        }
        confirmedNames.put(speakerLabel, name.strip());
    }

    public Optional<String> confirmedName(String speakerLabel) {
        return Optional.ofNullable(confirmedNames.get(speakerLabel));
    }
}

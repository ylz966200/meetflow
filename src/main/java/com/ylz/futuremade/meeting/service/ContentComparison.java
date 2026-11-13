package com.ylz.futuremade.meeting.service;

/** 用于比较会议正文：统一换行并忽略首尾空白，保留正文内部空格及段落。 */
public final class ContentComparison {
    private ContentComparison() { }

    public static String normalize(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n').strip();
    }
}

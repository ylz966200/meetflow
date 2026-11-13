package com.ylz.futuremade.meeting.storage;
import java.io.InputStream;
/** 录音对象存储的最小能力。 */
public interface AudioObjectStorage {
    /** 检查存储配置是否可用，不执行网络请求。 */
    void requireConfigured();
    /** 上传录音，不触发转写。 */
    void upload(String objectKey,InputStream content,long size);
    /** 生成临时只读链接；链接不得记录到日志或数据库。 */
    String signedGetUrl(String objectKey);
    /** 删除录音对象，重复删除应安全。 */
    void delete(String objectKey);
}

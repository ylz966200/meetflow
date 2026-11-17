package com.ylz.futuremade.meeting.storage;

import com.volcengine.tos.TOSV2ClientBuilder;
import com.volcengine.tos.comm.HttpMethod;
import com.volcengine.tos.model.object.PreSignedURLInput;
import com.volcengine.tos.model.object.PutObjectInput;
import com.volcengine.tos.model.object.DeleteObjectInput;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;

/**
 * 火山 TOS 私有桶录音上传与 GET 临时授权。
 */
@Component
public class TosAudioObjectStorage implements AudioObjectStorage {
    private final String endpoint, region, bucket, accessKey, secretKey;
    private final long expires;

    public TosAudioObjectStorage(@Value("${meetflow.tos.endpoint:}") String endpoint, @Value("${meetflow.tos.region:}") String region,
                                 @Value("${meetflow.tos.bucket:}") String bucket, @Value("${meetflow.tos.access-key:}") String accessKey,
                                 @Value("${meetflow.tos.secret-key:}") String secretKey, @Value("${meetflow.tos.signed-url-seconds:14400}") long expires) {
        this.endpoint = endpoint;
        this.region = region;
        this.bucket = bucket;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.expires = expires;
    }

    @Override
    public void requireConfigured() {
        if (endpoint.isBlank() || region.isBlank() || bucket.isBlank() || accessKey.isBlank() || secretKey.isBlank() || expires < 1 || expires > 604800)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TOS 配置不完整或链接有效期无效");
    }

    @Override
    public void upload(String objectKey, InputStream content, long size) {
        requireConfigured();
        try (var client = new TOSV2ClientBuilder().build(region, endpoint, accessKey, secretKey)) {
            client.putObject(new PutObjectInput().setBucket(bucket).setKey(objectKey).setContent(content).setContentLength(size).setForbidOverwrite(true));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOS 上传失败，请检查配置");
        }
    }

    @Override
    public String signedGetUrl(String objectKey) {
        requireConfigured();
        try (var client = new TOSV2ClientBuilder().build(region, endpoint, accessKey, secretKey)) {
            return client.preSignedURL(new PreSignedURLInput().setBucket(bucket).setKey(objectKey).setHttpMethod(HttpMethod.GET).setExpires(expires)).getSignedUrl();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOS 生成访问链接失败，请检查配置");
        }
    }

    @Override
    public void delete(String objectKey) {
        requireConfigured();
        try (var client = new TOSV2ClientBuilder().build(region, endpoint, accessKey, secretKey)) {
            client.deleteObject(new DeleteObjectInput().setBucket(bucket).setKey(objectKey));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOS 录音删除失败，请稍后重试");
        }
    }
}

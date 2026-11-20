package com.ylz.futuremade.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.knowledge.model.dto.KnowledgeAnswer;
import com.ylz.futuremade.knowledge.model.dto.MultiMeetingAnswer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 会议问答精确缓存，Redis 不可用时回退至正常问答流程。
 */
@Slf4j
@Service
public class KnowledgeAnswerCacheService {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final boolean enabled;
    private final Duration ttl;
    private final String chatModel;
public KnowledgeAnswerCacheService(StringRedisTemplate redis, ObjectMapper json,
                                       @Value("${meetflow.knowledge-cache.enabled:true}") boolean enabled,
                                       @Value("${meetflow.knowledge-cache.ttl:24h}") Duration ttl,
                                       @Value("${meetflow.ai.default-model:deepSeekChatModel}") String chatModel) {
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("问答缓存有效期必须大于零");
        this.redis = redis;
        this.json = json;
        this.enabled = enabled;
        this.ttl = ttl;
        this.chatModel = chatModel;
    }

    /**
     * 用户、会议、纪要版本及提示词版本分别隔离缓存。
     */
    public String key(String meetingId, long actor, int version, long promptId, String embeddingModel, String question) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            String modelHash = HexFormat.of().formatHex(digest.digest((chatModel + "\n" + embeddingModel).getBytes(StandardCharsets.UTF_8)));
            String questionHash = HexFormat.of().formatHex(digest.digest(question.strip().getBytes(StandardCharsets.UTF_8)));
            return "meetflow:knowledge:qa:v3:" + actor + ":" + meetingId + ":" + version + ":" + promptId + ":" + modelHash + ":" + questionHash;
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    /**
     * 查询失败时不影响实时检索；日志不包含问题及正文。
     */
    public KnowledgeAnswer get(String key) {
        if (!enabled) return null;
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? null : json.readValue(value, KnowledgeAnswer.class);
        } catch (Exception ex) {
            log.warn("知识问答缓存读取失败，回退实时问答：{}", ex.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 仅由已完成的问答调用，序列化内容存于 Redis 而非日志。
     */
    public void put(String key, KnowledgeAnswer value) {
        if (!enabled) return;
        try {
            redis.opsForValue().set(key, json.writeValueAsString(value), ttl);
        } catch (Exception ex) {
            log.warn("知识问答缓存写入失败：{}", ex.getClass().getSimpleName());
        }
    }

    /** 查询多会议问答缓存。 */
    public MultiMeetingAnswer getMulti(String key) {
        if (!enabled) return null;
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? null : json.readValue(value, MultiMeetingAnswer.class);
        } catch (Exception ex) {
            log.warn("多会议问答缓存读取失败：{}", ex.getClass().getSimpleName());
            return null;
        }
    }

    /** 保存多会议问答缓存。 */
    public void putMulti(String key, MultiMeetingAnswer value) {
        if (!enabled) return;
        try {
            redis.opsForValue().set(key, json.writeValueAsString(value), ttl);
        } catch (Exception ex) {
            log.warn("多会议问答缓存写入失败：{}", ex.getClass().getSimpleName());
        }
    }

}

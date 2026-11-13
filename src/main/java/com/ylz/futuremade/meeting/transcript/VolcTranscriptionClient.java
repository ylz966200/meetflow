package com.ylz.futuremade.meeting.transcript;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 火山引擎大模型录音文件识别标准版客户端。 */
public final class VolcTranscriptionClient {
    private static final Logger log = LoggerFactory.getLogger(VolcTranscriptionClient.class);
    /** 火山录音文件识别标准版协议的提交和查询路径。 */
    private static final String SUBMIT_PATH = "/api/v3/auc/bigmodel/submit";
    private static final String QUERY_PATH = "/api/v3/auc/bigmodel/query";
    /** 火山录音文件识别协议指定的模型名称。 */
    private static final String MODEL_NAME = "bigmodel";
    private static final String SUCCESS = "20000000";
    /** 火山协议：任务处理中。 */ private static final String PROCESSING = "20000001";
    /** 火山协议：任务正在排队。 */ private static final String QUEUED = "20000002";
    /** 火山协议：音频未检测到人声。 */ private static final String SILENCE = "20000003";
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI baseUri;
    private final String apiKey;
    private final String resourceId;
    private final String uid;
    private final String speakerVersion;

    public VolcTranscriptionClient(HttpClient client, ObjectMapper mapper, URI baseUri, String apiKey, String resourceId) {
        this(client, mapper, baseUri, apiKey, resourceId, new SpeechRequestProperties());
    }

    public VolcTranscriptionClient(HttpClient client, ObjectMapper mapper, URI baseUri, String apiKey, String resourceId,
                                   SpeechRequestProperties properties) {
        if (apiKey == null || apiKey.isBlank() || resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("Speech API key and resource ID are required");
        }
        if (properties == null || properties.getUid() == null || properties.getUid().isBlank()
                || properties.getSpeakerVersion() == null || properties.getSpeakerVersion().isBlank()) {
            throw new IllegalArgumentException("Speech uid and speaker version are required");
        }
        this.client = client;
        this.mapper = mapper;
        this.baseUri = baseUri;
        this.apiKey = apiKey;
        this.resourceId = resourceId;
        this.uid = properties.getUid();
        this.speakerVersion = properties.getSpeakerVersion();
    }

    /** 调用方必须在提交前保存请求 ID；请求超时不代表任务提交失败。 */
    public void submit(UUID requestId, URI audioUrl, String format) throws IOException, InterruptedException {
        if (audioUrl == null || !"https".equalsIgnoreCase(audioUrl.getScheme()) || audioUrl.getHost() == null
                || audioUrl.getUserInfo() != null) {
            throw new IllegalArgumentException("Audio URL must be an HTTPS URL without user information");
        }
        if (format == null || !List.of("wav", "mp3", "ogg").contains(format)) {
            throw new IllegalArgumentException("Unsupported audio format");
        }
        ObjectNode body = mapper.createObjectNode();
        body.putObject("user").put("uid", uid);
        body.putObject("audio").put("url", audioUrl.toString()).put("format", format);
        // 当前文字稿流程依赖分句及说话人标记，必须同时开启。
        body.putObject("request").put("model_name", MODEL_NAME)
                .put("enable_speaker_info", true).put("show_utterances", true).put("ssd_version", speakerVersion);
        HttpResponse<String> response = post(SUBMIT_PATH, requestId, body.toString());
        if (!SUCCESS.equals(code(response))) {
            log.warn("语音转写任务提交失败，请求ID={}，状态码={}", requestId, code(response));
            throw failure(response);
        }
        log.info("语音转写任务已受理，请求ID={}", requestId);
    }

    public TranscriptionQueryResult query(UUID requestId) throws IOException, InterruptedException {
        HttpResponse<String> response = post(QUERY_PATH, requestId, "{}");
        String status = code(response);
        if (PROCESSING.equals(status) || QUEUED.equals(status)) {
            return new TranscriptionQueryResult(State.PROCESSING, List.of(), null);
        }
        // 火山服务将未检测到人声作为独立结果返回，不需要无限重试。
        if (SILENCE.equals(status)) return new TranscriptionQueryResult(State.SUCCEEDED, List.of(), response.body());
        if (!SUCCESS.equals(status)) {
            log.warn("语音转写任务查询失败，请求ID={}，状态码={}", requestId, status);
            throw failure(response);
        }
        JsonNode result = mapper.readTree(response.body()).path("result");
        JsonNode utterances = result.path("utterances");
        if (!utterances.isArray()) throw new IOException("Speech response missing utterances");
        List<TranscriptSegment> segments = new ArrayList<>();
        for (JsonNode utterance : utterances) {
            JsonNode start = utterance.path("start_time"), end = utterance.path("end_time");
            if (!start.isIntegralNumber() || !end.isIntegralNumber()) throw new IOException("Invalid segment timestamps");
            // 不同语音接口版本可能返回 speaker 或 speaker_id，优先读取有效的 speaker。
            JsonNode additions = utterance.path("additions");
            String speaker = additions.path("speaker").asText("");
            if (speaker.isBlank() || "-1".equals(speaker)) speaker = additions.path("speaker_id").asText("");
            if (speaker.isBlank() || "-1".equals(speaker)) speaker = "unknown";
            try {
                segments.add(new TranscriptSegment(start.asLong(), end.asLong(), speaker, utterance.path("text").asText("")));
            } catch (IllegalArgumentException ex) {
                throw new IOException("Invalid transcription segment", ex);
            }
        }
        log.info("语音转写任务查询成功，请求ID={}，片段数={}", requestId, segments.size());
        return new TranscriptionQueryResult(State.SUCCEEDED, List.copyOf(segments), response.body());
    }

    private HttpResponse<String> post(String path, UUID requestId, String body) throws IOException, InterruptedException {
        if (requestId == null) throw new IllegalArgumentException("Request ID is required");
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json").header("X-Api-Key", apiKey)
                .header("X-Api-Resource-Id", resourceId).header("X-Api-Request-Id", requestId.toString())
                .header("X-Api-Sequence", "-1").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Speech service HTTP error: " + response.statusCode());
        }
        return response;
    }

    private String code(HttpResponse<String> response) throws IOException {
        return response.headers().firstValue("X-Api-Status-Code")
                .orElseThrow(() -> new IOException("Speech service status header missing"));
    }

    private IOException failure(HttpResponse<String> response) throws IOException {
        // 不输出响应消息，避免泄露音频地址或其他敏感信息。
        return new IOException("Speech service rejected request; status=" + code(response));
    }

    public enum State { PROCESSING, SUCCEEDED }
}

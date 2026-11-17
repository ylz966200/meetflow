package com.ylz.futuremade.knowledge.service;

import com.ylz.futuremade.knowledge.model.dto.MilvusConnectionView;
import com.ylz.futuremade.common.logging.ErrorDiagnostics;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.collection.ShowCollectionsParam;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Zilliz Cloud 的只读连接检查；不创建集合或修改数据。
 */
@Service
public class MilvusConnectionService {
    private final boolean enabled;
    private final String endpoint;
    private final String token;
    private final long timeoutSeconds;

    public MilvusConnectionService(@Value("${meetflow.knowledge.milvus.enabled:false}") boolean enabled,
                                   @Value("${meetflow.knowledge.milvus.endpoint:}") String endpoint,
                                   @Value("${meetflow.knowledge.milvus.token:}") String token,
                                   @Value("${meetflow.knowledge.milvus.timeout-seconds:10}") long timeoutSeconds) {
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.token = token;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * 向配置的服务发起只读请求，不记录连接地址和凭证。
     */
    public MilvusConnectionView check() {
        if (!enabled)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Milvus 尚未启用，请配置环境变量");
        if (endpoint.isBlank() || token.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "请配置 Zilliz Endpoint 和 Token");
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Zilliz Endpoint 格式无效");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Zilliz Endpoint 应为 HTTPS 地址");
        if (timeoutSeconds < 1 || timeoutSeconds > 60)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Milvus 连接超时配置无效");
        MilvusServiceClient client = null;
        try {
            var config = ConnectParam.newBuilder().withUri(endpoint).withToken(token)
                    .withConnectTimeout(timeoutSeconds, TimeUnit.SECONDS).build();
            client = new MilvusServiceClient(config);
            var result = client.withTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .showCollections(ShowCollectionsParam.newBuilder().build());
            if (result.getStatus() != R.Status.Success.getCode())
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Zilliz 连接检查失败，请检查地址、凭证和集群状态");
            return new MilvusConnectionView(true, "Zilliz 连接成功，可以开始准备向量检索实验。");
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            // SDK 错误可能含请求信息，避免在应用日志或接口中暴露凭证。
            ErrorDiagnostics.error("Zilliz 连接检查失败", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Zilliz 连接检查失败，请检查地址、凭证和网络");
        } finally {
            if (client != null) {
                try {
                    client.close(1);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}

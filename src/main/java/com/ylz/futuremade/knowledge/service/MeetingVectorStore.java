package com.ylz.futuremade.knowledge.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.param.*;
import io.milvus.param.collection.*;
import io.milvus.param.dml.*;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * 单会议纪要向量存储；仅存来源标识和向量，正文始终从数据库读取。
 */
@Service
public class MeetingVectorStore {
    private static final String VECTOR = "embedding";
    private final MilvusConnectionService connection;

    public MeetingVectorStore(MilvusConnectionService connection) {
        this.connection = connection;
    }

    /**
     * 幂等写入固定会议和纪要版本的条目向量。
     */
    public void upsert(String collection, String marker, String meetingId, int version, List<float[]> vectors) {
        var client = connection.open();
        try {
            prepare(client, collection, marker, vectors.get(0).length);
            var ids = new ArrayList<String>();
            var meetings = new ArrayList<String>();
            var versions = new ArrayList<Long>();
            var indexes = new ArrayList<Long>();
            for (int i = 0; i < vectors.size(); i++) {
                ids.add(meetingId + "_" + version + "_" + i);
                meetings.add(meetingId);
                versions.add((long) version);
                indexes.add((long) i);
            }
            var fields = List.of(new InsertParam.Field("id", ids), new InsertParam.Field("meeting_id", meetings),
                    new InsertParam.Field("minutes_version", versions), new InsertParam.Field("section_index", indexes),
                    new InsertParam.Field(VECTOR, vectors.stream().map(this::values).toList()));
            require(client.upsert(UpsertParam.newBuilder().withCollectionName(collection).withFields(fields).build()));
        } finally {
            connection.close(client);
        }
    }

    /**
     * 以会议和版本限制向量检索，返回条目序号与相似度。
     */
    public List<io.milvus.response.SearchResultsWrapper.IDScore> search(String collection, String marker, String meetingId, int version, float[] vector, int limit) {
        var client = connection.open();
        try {
            prepare(client, collection, marker, vector.length);
            String filter = "meeting_id == \"" + meetingId + "\" && minutes_version == " + version;
            var result = require(client.search(SearchParam.newBuilder().withCollectionName(collection).withVectorFieldName(VECTOR)
                    .withMetricType(MetricType.COSINE).withConsistencyLevel(ConsistencyLevelEnum.STRONG).withExpr(filter)
                    .withTopK(limit).withOutFields(List.of("section_index")).withVectors(List.of(values(vector))).build()));
            return new SearchResultsWrapper(result.getResults()).getIDScore(0);
        } finally {
            connection.close(client);
        }
    }

    private void prepare(MilvusServiceClient client, String name, String marker, int dimension) {
        if (!require(client.hasCollection(HasCollectionParam.newBuilder().withCollectionName(name).build()))) {
            var id = FieldType.newBuilder().withName("id").withDataType(DataType.VarChar).withPrimaryKey(true).withAutoID(false).withMaxLength(80).build();
            var meeting = FieldType.newBuilder().withName("meeting_id").withDataType(DataType.VarChar).withMaxLength(36).build();
            var version = FieldType.newBuilder().withName("minutes_version").withDataType(DataType.Int64).build();
            var index = FieldType.newBuilder().withName("section_index").withDataType(DataType.Int64).build();
            var vector = FieldType.newBuilder().withName(VECTOR).withDataType(DataType.FloatVector).withDimension(dimension).build();
            require(client.createCollection(CreateCollectionParam.newBuilder().withCollectionName(name).withDescription(marker)
                    .addFieldType(id).addFieldType(meeting).addFieldType(version).addFieldType(index).addFieldType(vector).build()));
            require(client.createIndex(CreateIndexParam.newBuilder().withCollectionName(name).withFieldName(VECTOR)
                    .withIndexName("minutes_vector_idx").withIndexType(IndexType.AUTOINDEX).withMetricType(MetricType.COSINE).withSyncMode(true).build()));
        }
        var schema = require(client.describeCollection(DescribeCollectionParam.newBuilder().withCollectionName(name).build())).getSchema();
        if (!marker.equals(schema.getDescription())) throw new IllegalStateException("知识集合模型标识不匹配");
        require(client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(name).withSyncLoad(true).build()));
    }

    private List<Float> values(float[] vector) {
        var result = new ArrayList<Float>(vector.length);
        for (float value : vector) result.add(value);
        return result;
    }

    private <T> T require(R<T> result) {
        if (result == null || result.getStatus() == null || result.getStatus() != R.Status.Success.getCode())
            throw new IllegalStateException("向量数据库操作失败");
        return result.getData();
    }
}

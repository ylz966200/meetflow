package com.ylz.futuremade.knowledge.service;

import com.ylz.futuremade.knowledge.model.dto.MilvusDemoHit;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.FieldSchema;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DescribeCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.dml.UpsertParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.index.DescribeIndexParam;
import io.milvus.response.SearchResultsWrapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** 仅操作专用实验集合，将固定虚构样本写入 Milvus 并执行相似检索。 */
@Service
public class MilvusDemoStore {
    private static final String ID_FIELD = "id";
    private static final String TEXT_FIELD = "content";
    private static final String VECTOR_FIELD = "embedding";
    private static final String INDEX_NAME = "demo_vector_idx";
    private final MilvusConnectionService connection;

    public MilvusDemoStore(MilvusConnectionService connection) { this.connection = connection; }

    /** 建立或复用已验证的实验集合，写入三条虚构记录并检索。 */
    public List<MilvusDemoHit> search(String collection, String schemaMarker, int dimension,
            List<String> texts, List<float[]> vectors, float[] queryVector) {
        MilvusServiceClient client = connection.open();
        try {
            prepareCollection(client, collection, schemaMarker, dimension);
            var fields = List.of(new InsertParam.Field(ID_FIELD, List.of("demo-1", "demo-2", "demo-3")),
                    new InsertParam.Field(TEXT_FIELD, texts),
                    new InsertParam.Field(VECTOR_FIELD, vectors.stream().map(this::toList).toList()));
            requireSuccess(client.upsert(UpsertParam.newBuilder().withCollectionName(collection).withFields(fields).build()), "写入虚构样本");
            var result = requireSuccess(client.search(SearchParam.newBuilder().withCollectionName(collection)
                    .withVectorFieldName(VECTOR_FIELD).withMetricType(MetricType.COSINE)
                    .withConsistencyLevel(ConsistencyLevelEnum.STRONG).withTopK(texts.size())
                    .withOutFields(List.of(TEXT_FIELD)).withVectors(List.of(toList(queryVector))).build()), "检索虚构样本");
            var hits = new ArrayList<MilvusDemoHit>();
            for (var item : new SearchResultsWrapper(result.getResults()).getIDScore(0)) {
                var hit = new MilvusDemoHit();
                hit.setId(item.getStrID());
                hit.setText(String.valueOf(item.get(TEXT_FIELD)));
                hit.setScore(item.getScore());
                hits.add(hit);
            }
            return hits;
        } finally { connection.close(client); }
    }

    private void prepareCollection(MilvusServiceClient client, String name, String marker, int dimension) {
        boolean exists = requireSuccess(client.hasCollection(HasCollectionParam.newBuilder().withCollectionName(name).build()), "查询实验集合");
        if (!exists) {
            var id = FieldType.newBuilder().withName(ID_FIELD).withDataType(DataType.VarChar).withPrimaryKey(true).withAutoID(false).withMaxLength(36).build();
            var content = FieldType.newBuilder().withName(TEXT_FIELD).withDataType(DataType.VarChar).withMaxLength(1024).build();
            var vector = FieldType.newBuilder().withName(VECTOR_FIELD).withDataType(DataType.FloatVector).withDimension(dimension).build();
            requireSuccess(client.createCollection(CreateCollectionParam.newBuilder().withCollectionName(name)
                    .withDescription(marker).addFieldType(id).addFieldType(content).addFieldType(vector).build()), "创建实验集合");
            createIndex(client, name);
        }
        var schema = requireSuccess(client.describeCollection(DescribeCollectionParam.newBuilder().withCollectionName(name).build()), "检查实验集合").getSchema();
        if (!marker.equals(schema.getDescription()) || !validFields(schema.getFieldsList(), dimension))
            throw new IllegalStateException("实验集合结构或模型标识不匹配，拒绝写入");
        var index = client.describeIndex(DescribeIndexParam.newBuilder().withCollectionName(name).withFieldName(VECTOR_FIELD).build());
        boolean missing = index.getStatus() != null && index.getStatus() == R.Status.IndexNotExist.getCode();
        if (!missing) {
            var existing = requireSuccess(index, "查询实验索引");
            missing = existing.getIndexDescriptionsCount() == 0;
            if (!missing && existing.getIndexDescriptionsList().stream().noneMatch(item -> INDEX_NAME.equals(item.getIndexName())))
                throw new IllegalStateException("实验集合存在非预期索引，拒绝写入");
        }
        if (missing)
            createIndex(client, name);
        requireSuccess(client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(name).withSyncLoad(true).build()), "加载实验集合");
    }

    private void createIndex(MilvusServiceClient client, String name) {
        requireSuccess(client.createIndex(CreateIndexParam.newBuilder().withCollectionName(name).withFieldName(VECTOR_FIELD)
                .withIndexName(INDEX_NAME).withIndexType(IndexType.AUTOINDEX).withMetricType(MetricType.COSINE).withSyncMode(true).build()), "创建向量索引");
    }

    private boolean validFields(List<FieldSchema> fields, int dimension) {
        boolean id = fields.stream().anyMatch(field -> ID_FIELD.equals(field.getName()) && field.getDataType() == DataType.VarChar && field.getIsPrimaryKey() && !field.getAutoID());
        boolean text = fields.stream().anyMatch(field -> TEXT_FIELD.equals(field.getName()) && field.getDataType() == DataType.VarChar);
        boolean vector = fields.stream().anyMatch(field -> VECTOR_FIELD.equals(field.getName()) && field.getDataType() == DataType.FloatVector
                && field.getTypeParamsList().stream().anyMatch(param -> "dim".equals(param.getKey()) && Integer.toString(dimension).equals(param.getValue())));
        return id && text && vector;
    }

    private List<Float> toList(float[] vector) {
        var values = new ArrayList<Float>(vector.length);
        for (float value : vector) values.add(value);
        return values;
    }

    private <T> T requireSuccess(R<T> result, String operation) {
        if (result == null || result.getStatus() == null || result.getStatus() != R.Status.Success.getCode())
            throw new IllegalStateException(operation + "失败，请检查集群状态和权限");
        return result.getData();
    }
}

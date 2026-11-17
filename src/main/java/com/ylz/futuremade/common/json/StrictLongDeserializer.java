package com.ylz.futuremade.common.json;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
/** 只接受 JSON 整数，避免外部协议时间戳被字符串或浮点数隐式转换。 */
public class StrictLongDeserializer extends JsonDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
            return (Long) context.handleUnexpectedToken(Long.class, parser);
        return parser.getLongValue();
    }
}

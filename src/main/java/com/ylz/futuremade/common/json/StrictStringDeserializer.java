package com.ylz.futuremade.common.json;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
/** 只接受 JSON 字符串，避免将模型输出的其他类型静默转换。 */
public class StrictStringDeserializer extends JsonDeserializer<String> {
    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING))
            return (String) context.handleUnexpectedToken(String.class, parser);
        return parser.getText();
    }
}

package mealplanner.agent;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mealplanner.domain.AppException;
import mealplanner.domain.Quantity;
import mealplanner.domain.Unit;

/**
 * Turns Claude's text reply into model objects. Claude is asked for JSON, but may wrap it in
 * prose or a ```json fence, so extractJson() finds the JSON part first.
 */
public class ResponseParser {

    private final ObjectMapper mapper;

    public ResponseParser() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Quantity.class, new QuantityDeserializer());
        mapper = new ObjectMapper()
                .registerModule(module)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public <T> T parse(String text, Class<T> type) {
        try {
            return mapper.treeToValue(extractJson(text), type);
        } catch (IOException | AppException | IllegalArgumentException e) {
            throw new ResponseParseException("Reply does not match " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    public <T> List<T> parseList(String text, Class<T> type) {
        JsonNode node = extractJson(text);
        if (!node.isArray()) {
            throw new ResponseParseException("Expected a JSON array");
        }
        List<T> list = new ArrayList<>();
        try {
            for (JsonNode element : node) {
                list.add(mapper.treeToValue(element, type));
            }
        } catch (IOException | AppException | IllegalArgumentException e) {
            throw new ResponseParseException("Reply does not match " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
        return list;
    }

    public String parseField(String text, String field) {
        JsonNode value = extractJson(text).get(field);
        if (value == null || value.isNull()) {
            throw new ResponseParseException("Reply has no field '" + field + "'");
        }
        return value.isTextual() ? value.asText() : value.toString();
    }

    private JsonNode extractJson(String text) {
        if (text == null) {
            throw new ResponseParseException("Empty reply");
        }
        int obj = text.indexOf('{');
        int arr = text.indexOf('[');
        int start = obj < 0 ? arr : (arr < 0 ? obj : Math.min(obj, arr));
        if (start < 0) {
            throw new ResponseParseException("Reply contains no JSON");
        }
        char close = text.charAt(start) == '{' ? '}' : ']';
        int end = text.lastIndexOf(close);
        if (end <= start) {
            throw new ResponseParseException("Reply contains incomplete JSON");
        }
        try {
            return mapper.readTree(text.substring(start, end + 1));
        } catch (IOException e) {
            throw new ResponseParseException("Reply contains invalid JSON", e);
        }
    }

    /** Reads {"amount": 12, "unit": "PIECE"} into the immutable Quantity value object. */
    private static class QuantityDeserializer extends JsonDeserializer<Quantity> {
        @Override
        public Quantity deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            if (!node.hasNonNull("amount") || !node.hasNonNull("unit")) {
                throw new IOException("quantity needs amount and unit");
            }
            return new Quantity(node.get("amount").asDouble(),
                    Unit.valueOf(node.get("unit").asText().trim().toUpperCase(Locale.ROOT)));
        }
    }
}
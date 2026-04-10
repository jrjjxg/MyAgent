package com.xg.platform.shared.runtime.graph;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.bsc.langgraph4j.serializer.plain_text.PlainTextStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlatformJacksonStateSerializer<State extends AgentState> extends PlainTextStateSerializer<State> {

    public static final String TYPE_PROPERTY = "@class";
    public static final String VALUE_PROPERTY = "@value";
    private static final String BYTE_ARRAY_CLASS_NAME = byte[].class.getName();

    private final ObjectMapper objectMapper;

    public PlatformJacksonStateSerializer(AgentStateFactory<State> stateFactory) {
        this(stateFactory, null);
    }

    public PlatformJacksonStateSerializer(AgentStateFactory<State> stateFactory,
                                          ObjectMapper objectMapper) {
        super(stateFactory);
        this.objectMapper = configureObjectMapper(objectMapper);
    }

    @Override
    public String contentType() {
        return "application/json";
    }

    @Override
    public String writeDataAsString(Map<String, Object> data) throws IOException {
        return objectMapper.writeValueAsString(toObjectNode(data));
    }

    @Override
    public Map<String, Object> readDataFromString(String string) throws IOException {
        JsonNode root = objectMapper.readTree(string);
        if (!(root instanceof ObjectNode objectNode)) {
            throw new IOException("Serialized state must be a JSON object");
        }
        return toMap(objectNode);
    }

    ObjectMapper objectMapper() {
        return objectMapper;
    }

    private ObjectNode toObjectNode(Map<String, Object> data) {
        ObjectNode root = objectMapper.createObjectNode();
        if (data == null || data.isEmpty()) {
            return root;
        }
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            root.set(entry.getKey(), toJsonNode(entry.getValue()));
        }
        return root;
    }

    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return NullNode.instance;
        }
        if (value instanceof JsonNode jsonNode) {
            return typedNode(jsonNode);
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return objectMapper.valueToTree(value);
        }
        if (value instanceof Map<?, ?> mapValue) {
            ObjectNode objectNode = objectMapper.createObjectNode();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                objectNode.set(String.valueOf(entry.getKey()), toJsonNode(entry.getValue()));
            }
            return objectNode;
        }
        if (value instanceof Iterable<?> iterable) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (Object element : iterable) {
                arrayNode.add(toJsonNode(element));
            }
            return arrayNode;
        }
        if (value.getClass().isArray()) {
            if (value instanceof byte[] bytes) {
                return typedNode(bytes);
            }
            ArrayNode arrayNode = objectMapper.createArrayNode();
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                arrayNode.add(toJsonNode(Array.get(value, index)));
            }
            return arrayNode;
        }
        return typedNode(value);
    }

    private ObjectNode typedNode(Object value) {
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put(TYPE_PROPERTY, value.getClass().getName());
        wrapper.set(VALUE_PROPERTY, objectMapper.valueToTree(value));
        return wrapper;
    }

    private Map<String, Object> toMap(ObjectNode objectNode) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            result.put(entry.getKey(), fromJsonNode(entry.getValue()));
        }
        return result;
    }

    private Object fromJsonNode(JsonNode node) throws IOException {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return switch (node.getNodeType()) {
            case STRING -> node.asText();
            case NUMBER -> node.numberValue();
            case BOOLEAN -> node.asBoolean();
            case BINARY -> node.binaryValue();
            case ARRAY -> toList((ArrayNode) node);
            case OBJECT -> fromObjectNode((ObjectNode) node);
            case POJO -> objectMapper.treeToValue(node, Object.class);
            default -> objectMapper.treeToValue(node, Object.class);
        };
    }

    private List<Object> toList(ArrayNode arrayNode) throws IOException {
        List<Object> result = new ArrayList<>();
        for (JsonNode element : arrayNode) {
            result.add(fromJsonNode(element));
        }
        return result;
    }

    private Object fromObjectNode(ObjectNode node) throws IOException {
        if (isTypedNode(node)) {
            return readTypedNode(node);
        }
        return toMap(node);
    }

    private boolean isTypedNode(ObjectNode node) {
        return node.has(TYPE_PROPERTY) && node.has(VALUE_PROPERTY) && node.size() == 2;
    }

    private Object readTypedNode(ObjectNode node) throws IOException {
        String className = node.path(TYPE_PROPERTY).asText("");
        if (className.isBlank()) {
            throw new IOException("Typed state entry is missing class name");
        }
        Class<?> type = resolveAllowedType(className);
        return objectMapper.treeToValue(node.path(VALUE_PROPERTY), type);
    }

    private Class<?> resolveAllowedType(String className) throws IOException {
        if (!isAllowedType(className)) {
            throw new IOException("Disallowed checkpoint type: " + className);
        }
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new IOException("Unknown checkpoint type: " + className, exception);
        }
    }

    private boolean isAllowedType(String className) {
        return BYTE_ARRAY_CLASS_NAME.equals(className)
                || className.startsWith("com.xg.platform.")
                || className.startsWith("com.fasterxml.jackson.databind.node.")
                || className.startsWith("java.time.");
    }

    private static ObjectMapper configureObjectMapper(ObjectMapper objectMapper) {
        ObjectMapper mapper = objectMapper == null
                ? JsonMapper.builder().build()
                : objectMapper.copy();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}

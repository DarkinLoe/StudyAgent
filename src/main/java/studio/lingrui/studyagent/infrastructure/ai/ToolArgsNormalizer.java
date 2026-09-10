package studio.lingrui.studyagent.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 工具参数归一化：把 JSON 按键排序、递归规范化，用于生成"重复调用指纹"。
 *
 * <p>教学要点：模型两次可能给出 {"a":1,"b":2} 与 {"b":2,"a":1} —— 内容相同、字符串不同。
 * 不归一化，重复调用检测就会失效。
 */
public final class ToolArgsNormalizer {

    private ToolArgsNormalizer() {
    }

    public static String normalize(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return "{}";
        }
        try {
            JsonNode node = mapper.readTree(json);
            return mapper.writeValueAsString(canonical(node, mapper));
        } catch (Exception e) {
            // 非合法 JSON（模型偶发输出异常）：退化为原始文本比较
            return json.trim();
        }
    }

    private static JsonNode canonical(JsonNode node, ObjectMapper mapper) {
        if (node.isObject()) {
            ObjectNode out = mapper.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            for (String name : names) {
                out.set(name, canonical(node.get(name), mapper));
            }
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = mapper.createArrayNode();
            node.forEach(child -> out.add(canonical(child, mapper)));
            return out;
        }
        return node;
    }
}

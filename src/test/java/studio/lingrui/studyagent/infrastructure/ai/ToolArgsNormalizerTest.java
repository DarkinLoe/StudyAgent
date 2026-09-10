package studio.lingrui.studyagent.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ToolArgsNormalizerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void keyOrderDoesNotMatter() {
        String a = ToolArgsNormalizer.normalize("{\"questionId\":12,\"userAnswer\":\"A\"}", mapper);
        String b = ToolArgsNormalizer.normalize("{ \"userAnswer\" : \"A\" , \"questionId\" : 12 }", mapper);
        assertEquals(a, b, "键顺序与空白不同应视为同一调用指纹");
    }

    @Test
    void nestedObjectsAreCanonicalized() {
        String a = ToolArgsNormalizer.normalize("{\"filter\":{\"b\":2,\"a\":1}}", mapper);
        String b = ToolArgsNormalizer.normalize("{\"filter\":{\"a\":1,\"b\":2}}", mapper);
        assertEquals(a, b);
    }

    @Test
    void differentValuesProduceDifferentFingerprint() {
        String a = ToolArgsNormalizer.normalize("{\"questionId\":12}", mapper);
        String b = ToolArgsNormalizer.normalize("{\"questionId\":13}", mapper);
        assertNotEquals(a, b);
    }

    @Test
    void blankOrInvalidJsonFallsBackSafely() {
        assertEquals("{}", ToolArgsNormalizer.normalize(null, mapper));
        assertEquals("{}", ToolArgsNormalizer.normalize("   ", mapper));
        assertEquals("not-json", ToolArgsNormalizer.normalize(" not-json ", mapper));
    }
}

package eai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class AppConfigTest {

    private static AppConfig config(String key, String value) {
        Properties props = new Properties();
        props.setProperty(key, value);
        return new AppConfig(props);
    }

    @Test
    void requireRejectsBlankValue() {
        assertThrows(IllegalStateException.class, () -> config("applicant.name", "  ").require("applicant.name"));
    }

    @Test
    void phoneAcceptsHyphenFormat() {
        assertEquals("010-1234-5678", config("applicant.phone", "010-1234-5678").phone());
    }

    @Test
    void phoneRejectsNumberWithoutHyphens() {
        assertThrows(IllegalStateException.class, () -> config("applicant.phone", "01012345678").phone());
    }
}

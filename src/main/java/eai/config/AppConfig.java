package eai.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * config.properties(git 제외)를 읽는다. 필수 값이 비어 있으면 해당 키 이름과 함께 즉시 실패한다.
 */
public class AppConfig {

    private static final Pattern PHONE = Pattern.compile("010-\\d{4}-\\d{4}");

    private final Properties props;

    AppConfig(Properties props) {
        this.props = props;
    }

    public static AppConfig load(Path path) throws IOException {
        if (Files.notExists(path)) {
            throw new IllegalStateException(path + " not found. Copy config.example.properties and fill it in.");
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        return new AppConfig(props);
    }

    public String require(String key) {
        String value = props.getProperty(key, "").trim();
        if (value.isEmpty()) {
            throw new IllegalStateException("Missing config value: " + key);
        }
        return value;
    }

    /** 전화번호는 복호화 키 생성에도 쓰이므로, API에 보낸 값과 같은 형식이어야 한다. */
    public String phone() {
        String phone = require("applicant.phone");
        if (!PHONE.matcher(phone).matches()) {
            throw new IllegalStateException("applicant.phone must look like 010-1234-5678: " + phone);
        }
        return phone;
    }
}

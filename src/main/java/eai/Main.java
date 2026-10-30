package eai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eai.config.AppConfig;
import eai.recruiting.RecruitingClient;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    /** API 응답 원본. 다음 단계에서 API를 다시 호출하지 않도록 로컬에 저장한다(git 제외). */
    static final Path RESPONSE_FILE = Path.of("data", "recruiting-response.json");

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.load(Path.of("config.properties"));
        String command = args.length > 0 ? args[0] : "";

        switch (command) {
            case "fetch" -> fetch(config);
            default -> System.out.println("Usage: ./gradlew run --args=\"fetch\"");
        }
    }

    private static void fetch(AppConfig config) throws Exception {
        RecruitingClient client = new RecruitingClient(
                config.require("recruiting.url"),
                config.require("recruiting.username"),
                config.require("recruiting.password"));
        JsonNode response = client.requestAssignment(
                config.require("applicant.name"), config.phone(), config.require("applicant.email"));

        Files.createDirectories(RESPONSE_FILE.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(RESPONSE_FILE.toFile(), response);

        System.out.println("Saved " + RESPONSE_FILE);
        response.fieldNames().forEachRemaining(field ->
                System.out.println("  " + field + " (" + response.get(field).getNodeType() + ")"));
    }
}

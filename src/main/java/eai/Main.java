package eai;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class Main {

    public static void main(String[] args) throws IOException {
        Properties config = loadConfig(Path.of("config.properties"));
        System.out.println("inspien-eai started: " + config.getProperty("applicant.name"));
    }

    static Properties loadConfig(Path path) throws IOException {
        if (Files.notExists(path)) {
            throw new IllegalStateException(path + " not found. Copy config.example.properties and fill it in.");
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        return props;
    }
}

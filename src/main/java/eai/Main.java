package eai;

import eai.config.AppConfig;
import java.io.IOException;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws IOException {
        AppConfig config = AppConfig.load(Path.of("config.properties"));
        System.out.println("inspien-eai started: " + config.require("applicant.name"));
    }
}

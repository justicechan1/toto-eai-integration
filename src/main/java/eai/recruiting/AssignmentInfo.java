package eai.recruiting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eai.crypto.ConnectionDecryptor;
import eai.db.DbInfo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;


public record AssignmentInfo(String applicantKey, DbInfo orderDb, DbInfo shipmentDb,
                             Map<String, String> ftp, byte[] sampleXml) {

    // 저장된 API 응답을 읽고 접속 정보 복호화
    public static AssignmentInfo load(Path responseFile, String phone) throws IOException {
        JsonNode response = new ObjectMapper().readTree(responseFile.toFile());
        ConnectionDecryptor decryptor = new ConnectionDecryptor(phone);

        return new AssignmentInfo(
                response.get("APPLICANT_KEY").asText(),  // 암호화되지 않은 값
                DbInfo.from(decryptor.decryptFields(response.get("ORDER_TB_CONN"))),
                DbInfo.from(decryptor.decryptFields(response.get("SHIPMENT_TB_CONN"))),
                decryptor.decryptFields(response.get("FTP_CONN")),
                Base64.getDecoder().decode(response.get("SAMPLE_DATA").asText()));  // EUC-KR XML 바이트
    }
}

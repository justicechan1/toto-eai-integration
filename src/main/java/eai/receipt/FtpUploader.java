package eai.receipt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

public class FtpUploader {

    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final String path;

    // 복호화한 FTP 접속 정보(URL, PORT, ID, PASSWORD, PATH)로 생성
    public FtpUploader(Map<String, String> ftp) {
        this.host = ftp.get("URL");
        this.port = Integer.parseInt(ftp.get("PORT"));
        this.user = ftp.get("ID");
        this.password = ftp.get("PASSWORD");
        this.path = ftp.get("PATH").endsWith("/") ? ftp.get("PATH") : ftp.get("PATH") + "/";
    }

    // 업로드 폴더의 파일명 목록
    public List<String> listFileNames() throws IOException {
        FTPClient ftp = connect();
        try {
            FTPFile[] files = ftp.listFiles(path);
            return Arrays.stream(files).filter(FTPFile::isFile).map(FTPFile::getName).toList();
        } finally {
            disconnect(ftp);
        }
    }

    // 여러 파일 업로드 (한 번 접속해서 처리), 실패한 파일명과 원인 반환
    public Map<String, String> upload(Map<String, byte[]> files) throws IOException {
        Map<String, String> failures = new LinkedHashMap<>();
        FTPClient ftp = connect();
        try {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                if (!ftp.storeFile(path + file.getKey(), new ByteArrayInputStream(file.getValue()))) {
                    failures.put(file.getKey(), ftp.getReplyString().trim());  // 실패해도 다음 파일 계속
                }
            }
        } finally {
            disconnect(ftp);
        }
        return failures;
    }

    // 접속, 로그인, 전송 설정
    private FTPClient connect() throws IOException {
        FTPClient ftp = new FTPClient();
        ftp.setControlEncoding("UTF-8");  // 한글 파일명
        ftp.setConnectTimeout(10_000);
        ftp.connect(host, port);
        if (!ftp.login(user, password)) {
            disconnect(ftp);
            throw new IOException("FTP login failed: " + ftp.getReplyString().trim());
        }
        ftp.enterLocalPassiveMode();  // 방화벽 환경에서 데이터 연결
        ftp.setFileType(FTP.BINARY_FILE_TYPE);  // BOM, 줄바꿈이 바뀌지 않도록
        return ftp;
    }

    // 로그아웃 후 연결 종료
    private static void disconnect(FTPClient ftp) {
        try {
            if (ftp.isConnected()) {
                ftp.logout();
                ftp.disconnect();
            }
        } catch (IOException ignored) {  // 종료 중 오류는 무시
        }
    }
}

package eai.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.regex.Pattern;

public record DbInfo(String url, String user, String password, String table) {

    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");  // 테이블명은 SQL에 직접 들어가므로 형식 제한

    // 복호화한 필드(URL, ID, PASSWORD, TABLE)로 생성, 테이블명 검사
    public static DbInfo from(Map<String, String> fields) {
        String table = fields.get("TABLE");
        if (table == null || !TABLE_NAME.matcher(table).matches()) {
            throw new IllegalStateException("Invalid table name: " + table);
        }
        return new DbInfo(fields.get("URL"), fields.get("ID"), fields.get("PASSWORD"), table);
    }

    // DB 연결
    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    // 출력용 문자열 (비밀번호 제외)
    @Override
    public String toString() {
        return "DbInfo[url=" + url + ", table=" + table + "]";
    }
}

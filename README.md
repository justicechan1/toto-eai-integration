# toto-eai-integration

토토 쇼핑몰 주문 / 회계 / 운송 연계 시스템 (인스피언 코딩테스트 과제).

주문 XML을 HTTP로 받아 주문 DB(ORDER_TB)에 저장하고, 주문별 영수증을 FTP로 회계팀에 보내고, 주기 배치로 운송 DB(SHIPMENT_TB)에 배송 정보를 등록한다. 모든 단계는 주문 ID별 처리 이력으로 남는다.

```
[주문 XML] --HTTP POST--> (1) 주문 수신 API --INSERT--> ORDER_TB (STATUS='N')
                                   |
                                   +--> (2) 영수증 CSV --업로드--> FTP

(3) 배송 배치 (60초 주기) --SELECT STATUS='N'--> ORDER_TB
                          --INSERT--> SHIPMENT_TB --UPDATE--> ORDER_TB (STATUS='Y')

(4) 모든 단계: logs/process.log 에 주문 ID별 처리 이력 (단계, 결과, 실패 원인)
```

## 요구 사항

- JDK 21 (Gradle Wrapper가 포함되어 있어 Gradle 설치는 필요 없다)
- 프로젝트 경로에 한글 등 ASCII가 아닌 문자가 없어야 한다. Windows에서 경로에 한글이 있으면 Gradle 테스트 실행 시 `ClassNotFoundException`이 난다.

## 설정

```bash
cp config.example.properties config.properties
```

`config.properties`에 아래 값을 채운다.

| 키 | 내용 |
|---|---|
| `applicant.name` | 지원자 이름. 영수증 파일명에 들어간다 |
| `applicant.phone` | 지원자 전화번호 (`010-0000-0000`). 접속 정보 복호화 키로도 쓰인다 |
| `applicant.email` | 지원자 이메일 |
| `recruiting.url` | 과제 정보 API 주소 (예시 파일에 입력되어 있음) |
| `recruiting.username`, `recruiting.password` | 과제 정보 API 계정 |

`config.properties`, `data/`(API 응답), `logs/`(처리 이력)는 개인정보와 접속 정보를 담으므로 git에서 제외한다.

## 실행

Windows는 `./gradlew` 대신 `gradlew.bat`을 쓴다.

### 1. 준비

```bash
./gradlew test                       # 단위 테스트 (H2 메모리 DB 사용, 제공 DB에 영향 없음)
./gradlew run --args="fetch"         # 과제 정보 API 호출 후 data/recruiting-response.json 에 저장 (최초 1회)
./gradlew run --args="decrypt"       # 접속 정보 복호화 확인 (비밀번호는 **** 로 표시)
./gradlew run --args="db-check"      # 주문/운송 DB 연결, 컬럼, 내 데이터 건수 확인
./gradlew run --args="parse"         # 샘플 주문 XML 파싱 결과 확인
```

### 2. 전체 흐름

터미널 1에서 서버를 띄운다. 주문 수신 API와 배송 배치(60초 주기)가 함께 실행된다.

```bash
./gradlew run --args="serve"
```

터미널 2에서 샘플 주문을 보낸다.

```bash
./gradlew run --args="send-sample"     # 샘플 주문 전체 전송 (send-sample 1 처럼 건수 지정 가능)
```

응답에는 저장된 주문 ID, 중복으로 건너뛴 건수, 주문자가 없는 상품, 영수증 업로드 결과가 담긴다. 같은 XML을 다시 보내도 이미 저장된 주문은 건너뛴다. 최대 60초 뒤 배송 배치가 STATUS='N' 주문을 SHIPMENT_TB에 등록하고 'Y'로 바꾼다.

다른 클라이언트로 직접 보낼 때는 다음과 같이 보낸다.

```bash
curl -X POST http://localhost:8080/orders -H "Content-Type: application/xml; charset=EUC-KR" --data-binary @order.xml
```

### 3. 확인과 재처리

```bash
./gradlew run --args="trace"           # 주문별 마지막 처리 단계 (어디까지 처리됐는지)
./gradlew run --args="trace A001"      # 주문 A001의 전체 처리 이력
./gradlew run --args="ftp-list"        # FTP에 올라간 내 영수증 파일 목록 (지원자 이름으로 시작하는 파일)
./gradlew run --args="receipts"        # 영수증이 없는 주문만 FTP에 다시 업로드 (실패분 재시도)
./gradlew run --args="ship"            # 배송 배치 1회 실행
```

처리 이력 형식은 다음과 같다.

```
2026-11-02 16:19:28.818 | A002 | SAVE | OK | USER04/ITEM15
2026-11-02 16:19:34.366 | A002 | RECEIPT | OK | 이름_A002_20261102161931.csv
2026-11-02 16:19:59.030 | A002 | SHIP | OK | SHIPMENT_ID=A002, STATUS=Y
```

Windows 콘솔에서는 출력의 한글이 `?`로 보일 수 있다. 콘솔 코드 페이지 문제이고, DB, FTP 파일명, `logs/process.log`에는 UTF-8로 정상 저장된다.

## 주요 설계 결정

| 제약 | 결정 |
|---|---|
| DB 권한이 SELECT/INSERT/UPDATE뿐 (DDL, DELETE 불가) | 이력 테이블 대신 로컬 로그로 처리를 추적한다. 저장 전에 검증하고, 한 요청은 한 트랜잭션으로 묶어 일부만 저장되지 않게 한다 |
| `STATUS`는 'N', 'Y'만 허용 | STATUS는 배송 등록 여부만 표시한다. 배송 배치는 'N'만 처리해 중복 등록을 막는다 |
| 주문 XML에 주문 번호가 없음 | 같은 지원자의 (USER_ID, ITEM_ID)가 이미 있으면 같은 주문으로 보고 건너뛴다 |
| HEADER 없는 ITEM이 있음 (70건 중 7건) | 이름, 주소를 채울 수 없어 저장하지 않고 응답과 이력에 남긴다 |
| FTP 덮어쓰기, 삭제 불가 | 파일명을 `이름_주문ID_업로드시각.csv`로 만들어 항상 새 파일로 올린다. FTP 파일 목록을 업로드 이력으로 써서 누락분만 재업로드한다 |
| 주문 DB와 운송 DB가 별개 | 한 트랜잭션으로 묶지 않는다. 배송 등록 전에 같은 ORDER_ID가 있는지 확인해, 재실행 시 중복 없이 복구한다 |
| ID 형식 `^[A-Z][0-9]{3}$` | ORDER_ID, SHIPMENT_ID 모두 A001 → A999 → B001 순서로 채번한다 |

자세한 내용은 [설계 문서](docs/DESIGN.md)에 있다. DB에서 당일 처리 결과를 확인하는 쿼리는 [시연용 조회 SQL](docs/demo.sql)에 있다.

## 구조

```
src/main/java/eai/
  Main.java            실행 명령 (fetch, serve, ship, trace 등)
  config/              config.properties 로딩, 필수값 검증
  recruiting/          과제 정보 API 클라이언트, 접속 정보 로딩
  crypto/              AES-128 접속 정보 복호화
  order/               주문 XML 파싱, ORDER_TB 저장, 조회
  server/              주문 수신 HTTP API (JDK HttpServer)
  receipt/             영수증 CSV 생성, FTP 업로드
  shipment/            배송 배치, SHIPMENT_TB 등록
  db/                  DB 접속 정보, ID 채번
  log/                 처리 이력 기록, 조회
```

## 기술 스택

Java 21, Gradle, Oracle JDBC(ojdbc11), Apache Commons Net(FTP), Jackson(JSON), JUnit 5, H2(테스트)

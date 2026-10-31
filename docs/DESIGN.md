# 설계 문서

토토 쇼핑몰 주문 / 회계 / 운송 연계(EAI) 시스템의 구조와 설계 결정을 정리한다.
작업 일정과 진행 상황은 GitHub Milestone과 Issue에서 관리한다.

## 전체 흐름

```
[주문 XML] --HTTP POST--> (1) 주문 수신 API --INSERT--> ORDER_TB (STATUS='N')
                                   |
                                   +--> (2) 영수증 생성 --업로드--> FTP

(3) 배송 배치 (주기 실행) --SELECT STATUS='N'--> ORDER_TB
                          --INSERT--> SHIPMENT_TB --UPDATE--> ORDER_TB (STATUS='Y')

(4) 모든 단계: 주문 ID별 처리 이력 로그 (단계, 결과, 실패 원인)
```

## 제공 DB 확인 결과

접속 정보로 직접 조회한 결과다.

| 항목 | 내용 | 영향 |
|---|---|---|
| DBMS | Oracle 19c, 문자셋 AL32UTF8 | 한글 저장 가능. JDBC 드라이버 ojdbc11 사용 |
| `ORDER_TB.STATUS` | CHECK 제약 `STATUS IN ('N', 'Y')` | STATUS는 배송 등록 여부만 표시한다 (N: 미배송, Y: 배송 등록 완료). 단계별 상태는 DB가 아니라 로그로 추적한다 |
| `ORDER_ID`, `SHIPMENT_ID` | CHECK 제약 `^[A-Z][0-9]{3}$` | 형식이 틀리면 DB가 거부한다. 채번 시 같은 규칙을 지킨다 |
| `ORDER_TB.CREATE_TIME`, `SHIPMENT_TB.CREATE_DATE` | 문서에 없는 컬럼, 기본값 `SYSDATE` | INSERT 시 값을 넣지 않는다. 시연 시 "당일 날짜 기준 조회"에 쓴다 |
| 테이블 소유자 | `RECRUIT` 스키마, 접속 계정은 `APPLICANT` | 테이블명만으로 접근된다 (시노님) |

## 제약에서 나온 설계 결정

| 제약 | 결정 |
|---|---|
| DB 권한 SELECT/INSERT/UPDATE만, DDL 불가 | 별도 이력 테이블을 만들 수 없으므로 처리 추적은 로컬 로그 파일로 한다 |
| STATUS는 'N', 'Y'만 허용 | 배송 배치는 STATUS='N'인 주문만 처리하고 'Y'로 바꿔 중복 등록을 막는다 |
| DELETE 불가 | 잘못 넣은 데이터를 지울 수 없으므로 저장 전에 검증한다. 같은 주문 재수신 시 PK 중복을 감지해 건너뛴다 |
| FTP 덮어쓰기/삭제 불가 | 파일명에 지원자 이름 + 주문 ID + 타임스탬프를 넣어 항상 새 파일로 올린다 |
| 샘플 XML이 EUC-KR | 수신·파싱 시 인코딩을 명시하고, 한글 깨짐을 테스트로 확인한다 |
| ID 형식: 대문자 1자 + 숫자 3자리 | 입력값 검증, SHIPMENT_ID 채번 규칙 정의 |
| 접속 정보는 암호화되어 제공 | 복호화 결과와 개인정보는 `config.properties`(git 제외)에만 둔다 |

## 주문 XML 구조

샘플 데이터(EUC-KR)를 분석한 결과다.

```xml
<HEADER>  <USER_ID/> <NAME/> <ADDRESS/>  </HEADER>           <!-- 주문자 -->
<ITEM>    <USER_ID/> <ITEM_ID/> <ITEM_NAME/> <PRICE/> </ITEM> <!-- 주문 상품 -->
```

| 특징 | 처리 |
|---|---|
| 루트 요소가 없다 (HEADER, ITEM이 최상위에 나열) | `<ROOT>`로 감싸서 파싱한다 |
| ITEM은 바로 앞 HEADER가 아니라 USER_ID로 주문자와 연결된다 | USER_ID 기준으로 HEADER와 ITEM을 합쳐 주문 1건(ORDER_TB 1행)을 만든다 |
| HEADER가 없는 USER_ID의 ITEM이 있다 (샘플 70건 중 7건) | 이름·주소를 채울 수 없어 저장하지 않고 따로 모아 기록한다 |
| ITEM이 없는 HEADER가 있다 | 주문이 아니므로 무시한다 |

## 주문 수신 API

`POST /orders` (본문: EUC-KR 주문 XML)

| 항목 | 결정 |
|---|---|
| ORDER_ID 채번 | 내 지원자 키의 가장 큰 ORDER_ID 다음 값 (A001 → A999 → B001). DB CHECK 제약과 같은 형식 |
| 중복 판단 | XML에 주문 번호가 없으므로 같은 지원자의 (USER_ID, ITEM_ID)가 이미 있으면 같은 주문으로 보고 건너뛴다. 같은 XML을 다시 보내도 중복 저장되지 않는다 |
| 트랜잭션 | 한 요청은 하나의 트랜잭션. 한 건이라도 실패하면 전부 취소한다 (DELETE가 불가하므로 일부만 저장된 상태를 만들지 않는다) |
| 신규 주문 STATUS | `N` (미배송) |
| 응답 | 200: 저장된 주문 ID, 중복 건수, 주문자 없는 상품 / 400: XML 오류 / 405: POST 외 / 413: 1MB 초과 / 500: DB 오류 |
| 테스트 | 제공 DB는 DELETE가 불가해 테스트 데이터를 지울 수 없으므로, 같은 제약을 건 메모리 DB(H2)로 테스트한다 |

## 기술 선택

- Java 21 + Gradle. 화면이 필요 없는 일반 애플리케이션이므로 웹 프레임워크는 쓰지 않는다.
- HTTP 수신은 JDK 내장 `HttpServer`, 암호화는 JDK `javax.crypto`를 쓴다.
- 외부 라이브러리는 꼭 필요한 것(JDBC 드라이버, FTP 클라이언트)만 추가한다.
- XML 파싱은 JDK DOM을 쓰고, 외부 입력이므로 DTD를 차단한다(XXE 방지).

## 미결정 사항 (샘플 데이터 확인 후 결정)
- 영수증 파일 형식 (CSV, TXT, XML 중 선택)

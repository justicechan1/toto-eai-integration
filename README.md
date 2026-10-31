# toto-eai-integration

토토 쇼핑몰 주문 / 회계 / 운송 연계 시스템 (인스피언 코딩테스트 과제).

## 실행

```bash
cp config.example.properties config.properties   # 값 채우기
./gradlew run --args="fetch"                     # 과제 정보 수신 후 data/에 저장
./gradlew run --args="decrypt"                   # 저장된 접속 정보 복호화 확인 (비밀번호 제외)
./gradlew run --args="parse"                     # 샘플 주문 XML 파싱 결과 확인
./gradlew run --args="db-check"                  # 주문/운송 DB 연결 및 테이블 확인
./gradlew run --args="serve"                     # 주문 수신 API 실행 (POST http://localhost:8080/orders, 본문: EUC-KR XML)
./gradlew run --args="send-sample 1"             # 다른 터미널에서 샘플 주문 1건만 API로 전송 (건수 생략 시 전체)
./gradlew run --args="receipts"                  # 영수증이 없는 주문의 영수증을 FTP에 업로드 (실패분 재시도)
```

`config.properties`에는 개인정보와 복호화된 접속 정보가 들어가므로 git에 올리지 않는다.

## 문서
- [설계 문서](docs/DESIGN.md)

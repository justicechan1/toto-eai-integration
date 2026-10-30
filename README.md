# toto-eai-integration

토토 쇼핑몰 주문 / 회계 / 운송 연계 시스템 (인스피언 코딩테스트 과제).

## 실행

```bash
cp config.example.properties config.properties   # 값 채우기
./gradlew run --args="fetch"                     # 과제 정보 수신 후 data/에 저장
```

`config.properties`에는 개인정보와 복호화된 접속 정보가 들어가므로 git에 올리지 않는다.

## 문서
- [설계 문서](docs/DESIGN.md)

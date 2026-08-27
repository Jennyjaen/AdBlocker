# AdBlocker 구현 가이드 (1~7단계)

> 이 문서는 구현 브리프 기준 **1~7단계**를 확인·진행하기 위한 메모입니다.  
> 프롬프트 튜닝, 자동 클릭, git commit/PR은 범위 밖입니다.

**레포:** `C:\Users\86952\AndroidStudioProjects\AdBlocker`  
**패키지:** `com.example.adblocker`  
**서비스:** `AdBlockerAccessibilityService`  
**모델 ID:** `GeminiConfig.MODEL_ID` → `gemini-2.5-flash`  
**API 키:** `local.properties`의 `GEMINI_API_KEY=` → `BuildConfig.GEMINI_API_KEY`

---

## 진행 현황 요약


| 단계  | 내용                                   | 상태       |
| --- | ------------------------------------ | -------- |
| 1   | Gradle / 매니페스트 / 키 / 접근성 XML + 빈 서비스 | ✅ 완료     |
| 2   | MainActivity 온보딩                     | ✅ 완료     |
| 3   | 실기기 접근성 버튼 동작 확인                     | ✅ 완료 (실기기 테스트 필요) |
| 4   | takeScreenshot + ImageUtils          | ⬜ 미구현    |
| 5   | NavBarDetector                       | ⬜ 미구현    |
| 6   | GeminiApiClient + placeholder 프롬프트   | ⬜ 미구현    |
| 7   | runPipeline + SpokenMessageBuilder   | ⬜ 미구현    |
| 8   | 연속 탭 cancel / onDestroy / recycle    | ⬜ 미구현    |


---



## 1~7단계 — 확인 체크리스트

각 단계마다 **무엇이 들어갔는지**, **어떻게 확인하는지**만 적었습니다.

---



### 1. Gradle / 매니페스트 / 키 / 접근성 XML + 빈 서비스 ✅

**구현된 파일**


| 파일                                                       | 역할                                                            |
| -------------------------------------------------------- | ------------------------------------------------------------- |
| `app/build.gradle.kts`                                   | `buildConfig = true`, OkHttp, coroutines, `GEMINI_API_KEY` 주입 |
| `gradle/libs.versions.toml`                              | OkHttp, coroutines 버전                                         |
| `app/src/main/AndroidManifest.xml`                       | `INTERNET`, 서비스 등록                                            |
| `app/src/main/res/xml/accessibility_service_config.xml`  | 접근성 능력 선언                                                     |
| `app/src/main/java/.../AdBlockerAccessibilityService.kt` | 버튼 → TTS                                                      |
| `app/src/main/java/.../GeminiConfig.kt`                  | `MODEL_ID = "gemini-2.5-flash"`                               |
| `app/src/main/res/values/strings.xml`                    | 접근성 서비스 설명 문자열                                                |


**확인 방법**

- [ ] Android Studio에서 빌드 성공
- [ ] 설정 → 접근성 목록에 **AdBlocker** 표시
- [ ] 서비스 ON 후, 다른 앱 위에서 **접근성 버튼** (제스처 내비: 보통 하단 두 손가락 스와이프)
- [ ] TTS: **「접근성 버튼이 동작합니다」**
- [ ] `onAccessibilityEvent`는 비어 있음 (자동 감지 없음)

**아직 하지 않는 것**

- 스크린샷, Gemini API, 파이프라인

---



### 2. MainActivity 온보딩 ✅

**구현된 파일**


| 파일                                            | 역할             |
| --------------------------------------------- | -------------- |
| `app/src/main/java/.../MainActivity.kt`       | Compose 온보딩 UI |
| `app/src/main/java/.../AccessibilityUtils.kt` | 서비스 ON/OFF 읽기  |
| `app/src/main/res/values/strings.xml`         | 온보딩 문구         |


**화면 구성**

1. 앱 설명
2. **접근성 서비스: 켜짐 / 꺼짐** (코드로 읽어서 표시)
3. **접근성 설정 열기** 버튼 → `Settings.ACTION_ACCESSIBILITY_SETTINGS`
4. `GEMINI_API_KEY` 없으면 **경고 카드** (화면 안 안내, 팝업 아님)
5. 사용법 (서비스 켠 뒤 앱 안 열어도 됨)

**확인 방법**

- [ ] 앱 실행 시 Hello 화면 없음, 온보딩만 보임
- [ ] 서비스 꺼짐 → **「꺼짐」** (빨간색)
- [ ] 설정에서 AdBlocker ON → 앱 복귀 → **「켜짐」**
- [ ] 키 없이 빌드 → 경고 카드 보임
- [ ] `local.properties`에 키 넣고 재빌드 → 경고 카드 사라짐
- [ ] 분석 로직은 Activity에 없음 (서비스만)

---



### 3. 실기기 접근성 버튼 동작 확인 ✅

**구현된 보강 (1~2번 위에 추가)**

| 파일 / 항목 | 역할 |
|-------------|------|
| `AdBlockerAccessibilityService.kt` | 버튼 클릭 Logcat (`AdBlockerA11y`), TTS 미준비 시 대기 후 재생 |
| `MainActivity.kt` | 서비스 **켜짐**일 때 **3단계 확인** 안내 카드 |
| `strings.xml` | TTS 문장, 확인 단계 문구 |

**확인 방법**

- [ ] AdBlocker 접근성 서비스 ON → 온보딩에 **「3단계: 접근성 버튼 동작 확인」** 카드 표시
- [ ] 홈/게임 등 **다른 앱** 화면에서 접근성 버튼
- [ ] **「접근성 버튼이 동작합니다」** TTS
- [ ] Logcat: `AdBlockerA11y` 태그에 `accessibility_button_clicked` 로그
- [ ] 앱 전용 스와이프 / 터치 탐색 / 플로팅 버튼 없음

**이 단계 통과 전에 4~7을 넣지 않는 이유**

버튼이 안 들어오면, 이후 버그가 캡처/API 문제인지 트리거 문제인지 구분이 안 됩니다.

---



### 4. takeScreenshot + ImageUtils ⬜

**만들 파일 (예정)**


| 파일                                      | 역할                                                 |
| --------------------------------------- | -------------------------------------------------- |
| `ImageUtils.kt`                         | JPEG 축소·Base64, `cropCorner(..., 0.3)` 4장, recycle |
| `AdBlockerAccessibilityService.kt` (수정) | `takeScreenshot()` 연동                              |


**동작**

- `takeScreenshot(Display.DEFAULT_DISPLAY, …)`
- `wrapHardwareBuffer` → `copy(ARGB_8888)` → `hardwareBuffer.close()` **필수**
- 성공/실패 TTS만 (아직 API 호출 없음)
- 실패(FLAG_SECURE 등): **「이 화면은 캡처할 수 없습니다.」**

**확인 방법**

- [ ] 일반 앱 화면 → 캡처 성공 TTS (또는 다음 단계까지 임시 문장)
- [ ] 은행/FLAG_SECURE 앱 → 캡처 실패 TTS
- [ ] Logcat에 하드웨어 버퍼 관련 누수 없음

---



### 5. NavBarDetector ⬜

**만들 파일 (예정)**


| 파일                  | 역할                                           |
| ------------------- | -------------------------------------------- |
| `NavBarDetector.kt` | `service.windows` → `navBarVisible: Boolean` |


**동작**

- 스크린샷에서 바를 찾지 않음
- `TYPE_SYSTEM` 하단 창 또는 활성 앱 bounds vs 화면 높이
- **하단바만으로 광고 판정 금지** (약한 힌트)

**확인 방법**

- [ ] Logcat에 `navBarVisible=true/false` 출력
- [ ] 몰입 모드(바 숨김) vs 일반 앱에서 값이 달라짐

---



### 6. GeminiApiClient + placeholder 프롬프트 ⬜

**만들 파일 (예정)**


| 파일                   | 역할                                                    |
| -------------------- | ----------------------------------------------------- |
| `GeminiApiClient.kt` | REST `generateContent`, JSON schema, 타임아웃             |
| `GeminiPrompts.kt`   | `"TODO: classify prompt"` / `"TODO: locate prompt"` 만 |
| `Models.kt`          | `ScreenQuadrant`, `AdControlsResult` 등                |


**동작**

- `classifyAdScreen(imageJpegB64): Boolean` — 스키마 `{ "is_ad": boolean }`
- `locateAdControls(...)`: `close_button`, `skip_indicator` enum
- `responseMimeType=application/json`, temperature 0, 재시도 없음
- 키 없으면 호출하지 않음

**확인 방법**

- [ ] 키 있을 때 네트워크 요청 1회 (Logcat/Charles)
- [ ] placeholder여도 JSON 파싱까지 도달 (정답률 무시)
- [ ] 키 없음 / 비행기 모드 → 크래시 없음

---



### 7. runPipeline + SpokenMessageBuilder ⬜

**만들 파일 (예정)**


| 파일                                      | 역할               |
| --------------------------------------- | ---------------- |
| `SpokenMessageBuilder.kt`               | 4분면 → 고정 한국어 TTS |
| `AdBlockerAccessibilityService.kt` (수정) | `runPipeline` 조립 |


**파이프라인 순서**

1. 이전 job cancel → **「화면을 분석 중입니다」**
2. 스크린샷 + `navBarVisible` (병렬 가능)
3. 캡처 실패 → TTS 종료
4. 키 없음 → TTS 종료
5. classify (전체 1장)
6. `is_ad=false` → **「지금은 광고 화면이 아닙니다.」**
7. `is_ad=true` → locate (4모서리) → 템플릿 TTS

**결합 규칙 (바꾸지 않음)**


| 비전 is_ad | 하단바 | 최종                  |
| -------- | --- | ------------------- |
| true     | *   | 광고 → 위치 탐지          |
| false    | *   | 광고 아님 (뒤집지 않음)      |
| API 실패   | *   | 에러 TTS (하단바로 판정 금지) |


**확인 방법**

- [ ] 일반 화면 → classify 후 TTS (placeholder라 내용은 틀려도 됨)
- [ ] TTS는 모델이 아니라 **고정 템플릿** 문장
- [ ] 자동 클릭 없음

---



### 8. 연속 탭 cancel / onDestroy / recycle ⬜

(브리프 7번 구현 순서의 마지막 항목)

- [ ] 연속 버튼 탭 → 이전 Job cancel, 최신만 처리
- [ ] TTS `QUEUE_FLUSH`
- [ ] `CoroutineScope(SupervisorJob() + Main)`, `onDestroy`에서 cancel + TTS shutdown
- [ ] 쓴 Bitmap recycle

---



## 부록 A — 단계별 상세 설명 (무엇을 / 왜)



### 1번: Gradle / 매니페스트 / 키 / 접근성 XML + 빈 서비스

**목적:** 분석 엔진이 아니라, **접근성 버튼 → 우리 서비스 → TTS**까지 OS가 우리 코드를 호출하게 만드는 기반.


| 항목                                 | 무엇을                            | 왜                         |
| ---------------------------------- | ------------------------------ | ------------------------- |
| OkHttp + coroutines                | 나중에 Gemini HTTP, 백그라운드 작업      | UI(서비스)가 네트워크 대기 중 죽지 않게  |
| `BuildConfig.GEMINI_API_KEY`       | `local.properties` 키를 빌드 시만 주입 | git/소스에 키 안 남김, 서버 없이 개인용 |
| `GeminiConfig.MODEL_ID`            | 모델 id 한곳                       | URL 여러 파일에 흩뿌리지 않음        |
| `INTERNET`                         | 네트워크 권한                        | 없으면 Gemini 호출 차단          |
| 서비스 매니페스트 등록                       | 설정 > 접근성 목록에 노출                | 등록 없으면 사용자가 켤 수 없음        |
| `accessibility_service_config.xml` | 캡처·버튼·윈도우 읽기 선언                | OS가 능력을 서비스에 부여           |
| 빈 서비스 + TTS                        | 버튼이 **우리 프로세스에 들어왔는지** 확인      | 캡처/API 전에 트리거 회로만 닫음      |
| `onAccessibilityEvent` 비움          | 자동 화면 감지 안 함                   | 버튼 누를 때만 1회 분석            |


**accessibility_service_config.xml 플래그**


| 플래그                              | 역할                                            |
| -------------------------------- | --------------------------------------------- |
| `flagRequestAccessibilityButton` | 시스템 접근성 버튼 → `onAccessibilityButtonClicked()` |
| `canTakeScreenshot`              | API 30 `takeScreenshot()` (minSdk 30 이유)      |
| `canRetrieveWindowContent`       | `windows`로 하단 내비 바 등 (5번)                     |


---



### 2번: MainActivity 온보딩

**목적:** 설치 직후 사용자가 **서비스를 켜고, 키 상태를 아는** 화면. 분석은 하지 않음.


| 항목              | 무엇을                                         | 왜                                   |
| --------------- | ------------------------------------------- | ----------------------------------- |
| 서비스 ON/OFF 표시   | 코드로 OS 상태 **읽어서** 앱에 표시                     | 설정 갔다 와도 앱에서 바로 확인                  |
| 설정 열기 버튼        | `ACTION_ACCESSIBILITY_SETTINGS`             | 목록은 깊어서 입구까지 앱이 연다. **ON은 사용자가 직접** |
| API 키 경고 카드     | `BuildConfig.GEMINI_API_KEY` 비어 있으면 화면 안 카드 | 크래시 대신 미리 알림. 팝업/토스트 아님             |
| Activity에 분석 없음 | 캡처·API·TTS는 서비스                             | 분석 시점에 **광고/게임 화면**이 앞에 있어야 함       |


**사용자 흐름**

```
앱 설치 → MainActivity(온보딩)
       → 접근성 설정에서 AdBlocker ON
       → 다른 앱으로 나감
       → 접근성 버튼 → 서비스가 분석 (Activity 포그라운드 불필요)
```

---



### 3번: 실기기 확인 게이트

**목적:** 1~2가 만든 **트리거 루프**(버튼 → 서비스 → TTS)가 실기기에서 동작하는지 검증. 통과 전 4~7 넣지 않음.

**추가한 것**

- 서비스: Logcat `AdBlockerA11y` / `accessibility_button_clicked`, TTS 준비 전 버튼 탭 시 대기 후 재생
- 온보딩: 서비스 **켜짐**일 때만 **3단계 확인** 카드 (다른 앱에서 버튼 누르는 방법)

**사용자가 할 일:** 실기기에서 버튼 → TTS 한 번 들어보기 (에뮬레이터는 접근성 버튼이 없거나 다를 수 있음).

---



### 4번: takeScreenshot + ImageUtils

**목적:** 버튼 → **현재 화면 JPEG**까지. 아직 Gemini 없음.

- MediaProjection 다이얼로그 없이 API 30+ 접근성 1회 허용으로 온디맨드 캡처
- 하드웨어 Bitmap은 `compress` 불가 → 소프트웨어 복사 후 JPEG
- `hardwareBuffer.close()` 필수 (누수 방지)
- FLAG_SECURE → onFailure → 고정 TTS

---



### 5번: NavBarDetector

**목적:** 스크린샷이 아닌 **접근성 windows**로 하단 내비 바 보임 여부 (약한 힌트).

- 게임 몰입 모드 vs 광고 Activity에서 바 재등장 구분용
- **하단바만으로 광고 판정 금지**

---



### 6번: GeminiApiClient

**목적:** 프롬프트 내용 제외하고 **호출·스키마·파싱·에러** 골격.

- `responseMimeType=application/json` + `responseSchema`
- 자연어 파싱 금지, 필드만 읽음
- 프롬프트는 `GeminiPrompts.kt`에 TODO placeholder
- 재시도 없음 (버튼 재탭 = 재시도)

---



### 7번: runPipeline + SpokenMessageBuilder

**목적:** 캡처 → 분류 → (조건부) 위치 → **고정 한국어 TTS**.

- 모델이 문장 짓지 않음 (환각 방지)
- 4분면 enum → 「왼쪽 위 / 오른쪽 위 / …」 맵만
- classify `true`일 때만 locate (비용·지연)

---



## 부록 B — Android 파일 종류와 역할 (초보용)

앱은 **코드(.kt)** 와 **설정 파일**이 같이 동작합니다.


| 종류                   | 이 프로젝트 예                                   | 하는 일                                 |
| -------------------- | ------------------------------------------ | ------------------------------------ |
| **Activity**         | `MainActivity.kt`                          | 아이콘 눌러 **열리는 화면**. 온보딩만.             |
| **Service**          | `AdBlockerAccessibilityService.kt`         | 화면 없이 **백그라운드**. 버튼 대기·캡처·TTS.       |
| **매니페스트**            | `AndroidManifest.xml`                      | OS용 **명함**: 권한, Activity/Service 목록. |
| **리소스 XML**          | `res/xml/accessibility_service_config.xml` | 접근성 서비스 **능력 목록**.                   |
| **Gradle**           | `app/build.gradle.kts`                     | **빌드 설명서**: SDK, 라이브러리, 키 주입.        |
| **local.properties** | git 제외                                     | 이 PC만: SDK 경로, `GEMINI_API_KEY=`.    |


코드를 잘 짜도 **매니페스트에 서비스가 없으면** 설정 목록에 안 나옵니다.  
**Gradle에 키 주입이 없으면** 앱은 Gemini 주소를 알아도 인증 문자열이 없습니다.

---



## 부록 C — 왜 접근성 프로젝트인가

한 줄: **다른 앱 위에서, 사용자가 누른 순간 화면을 찍고 말로 안내하려면 Android가 허용하는 공식 경로가 AccessibilityService**이기 때문.


| 필요한 것        | 일반 앱                      | 접근성 서비스                               |
| ------------ | ------------------------- | ------------------------------------- |
| 다른 앱 화면 캡처   | ❌ (MediaProjection 다이얼로그) | ✅ `takeScreenshot()` (API 30+, 1회 설정) |
| 앱 밖에서 버튼 트리거 | ❌ (오버레이 권한 필요)            | ✅ 접근성 버튼                              |
| 화면 창/노드 정보   | ❌                         | ✅ `windows`, 노드 트리 (2안)               |


이 앱은 광고 **차단(VPN/필터)** 이 아니라, 전면 광고 **위치를 TTS로 안내** (자동 클릭 없음)입니다.

원 설계: `c:\Users\86952\AdGuardAssistant\docs\PROJECT.md`  
(Claude/Tool Use → 이 프로젝트는 **Gemini Flash + JSON schema**)

---



## 부록 D — Gradle 키 주입 (자세히)

**흐름**

```
local.properties          app/build.gradle.kts          앱 코드
GEMINI_API_KEY=abc...  →  buildConfigField(...)     →  BuildConfig.GEMINI_API_KEY
(gitignore)               (빌드할 때만 읽음)            (생성된 상수)
```

**왜 이렇게 하나**

- 소스에 키 문자열 → git 유출
- 개인용이라 로그인 서버 없음 → BuildConfig 주입이 가장 단순
- 키 없으면 빈 문자열 → 온보딩 경고 + API 호출 스킵

**OkHttp / coroutines (1번에 같이 넣은 이유)**

- OkHttp: Gemini REST 호출용 HTTP 클라이언트
- coroutines: 서비스에서 IO(네트워크) 후 Main(TTS) 전환

---



## 부록 E — 매니페스트 서비스 등록 (자세히)

**INTERNET**

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

→ Gemini HTTPS 허용.

**서비스 블록**

```xml
<service
    android:name=".AdBlockerAccessibilityService"
    android:exported="false"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```


| 속성                           | 의미                   |
| ---------------------------- | -------------------- |
| `BIND_ACCESSIBILITY_SERVICE` | 설정 앱만 이 서비스를 붙일 수 있음 |
| `intent-filter`              | 설정 > 접근성 목록에 표시      |
| `meta-data` → XML            | 캡처·버튼·윈도우 읽기 선언      |


**설치 후 흐름**

```
설정에서 AdBlocker ON
  → OS가 AdBlockerAccessibilityService 기동
  → 접근성 버튼이 이 서비스에 연결
  → onAccessibilityButtonClicked()
  → (현재) TTS 「접근성 버튼이 동작합니다」
```

코드로 접근성 **자동 ON 불가** (OS 정책). 온보딩은 설정 화면까지만 연다.

---



## 부록 F — 온보딩 「경고」와 「ON 표시」 (2번 질문 정리)



### ON 여부 표시 ≠ 설정 redirection


|     | ON 표시                                               | 설정 버튼                           |
| --- | --------------------------------------------------- | ------------------------------- |
| 역할  | 앱에 **켜짐/꺼짐** 텍스트                                    | 설정 앱 **열기**                     |
| 방법  | `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` 읽기 | `ACTION_ACCESSIBILITY_SETTINGS` |
| 시점  | `onResume`마다 갱신                                     | 사용자가 탭할 때                       |


설정으로 보내는 것만으로 ON 표시를 대체하지 **않습니다.**  
앱이 먼저 상태를 보여 주고, 꺼져 있으면 버튼으로 설정까지 안내합니다.

### API 키 「경고」

- **시스템 팝업/토스트 아님**
- Compose **경고 카드** (errorContainer 색)
- `BuildConfig.GEMINI_API_KEY.isBlank()` 일 때만 표시
- 키 넣고 **재빌드**해야 사라짐 (런타임에 파일 수정만으로는 BuildConfig 안 바뀜)

---



## 부록 G — 완료 기준 (브리프 8절)

전체 구현 후:

- [ ] 접근성 ON → 버튼 → TTS
- [ ] 일반 화면: 캡처 + (키 있으면) classify + 템플릿 TTS
- [ ] FLAG_SECURE: 캡처 실패 문장
- [ ] 키 없음 / 비행기 모드: 크래시 없이 TTS
- [ ] 자동 클릭 없음, 터치 탐색 없음
- [ ] `GeminiPrompts.kt`에 TODO placeholder만
- [ ] 이미지·키 git 없음
- [ ] `scripts/eval_gemini_flash.py` 미수정

---



## 부록 H — 권장 파일 트리 (완성 시)

```
app/src/main/java/com/example/adblocker/
├── MainActivity.kt                 ✅
├── AccessibilityUtils.kt           ✅
├── AdBlockerAccessibilityService.kt ✅ (4~8에서 확장)
├── GeminiConfig.kt                 ✅
├── GeminiApiClient.kt              ⬜
├── GeminiPrompts.kt                ⬜
├── ImageUtils.kt                   ⬜
├── Models.kt                       ⬜
├── NavBarDetector.kt               ⬜
└── SpokenMessageBuilder.kt         ⬜

app/src/main/res/xml/
└── accessibility_service_config.xml ✅
```

---

*마지막 갱신: 1~2단계 구현 반영 기준*
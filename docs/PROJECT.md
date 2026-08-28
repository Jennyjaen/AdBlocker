# 개인용 접근성 어시스턴트 — 설계 및 구현 가이드

> 개인 사용 목적(배포 없음) · Android · 자동 클릭 없음(안내만) · 사용자 버튼으로 온디맨드 실행

**레포:** `C:\Users\86952\AndroidStudioProjects\AdBlocker`  
**패키지:** `com.example.adblocker`  
**서비스:** `AdBlockerAccessibilityService`  
**구현 체크리스트:** [`docs/IMPLEMENTATION_GUIDE.md`](IMPLEMENTATION_GUIDE.md)  
**모델:** `GeminiConfig.MODEL_ID` → `gemini-2.5-flash` (`GeminiApiClient`, JSON schema)  
**API 키:** `local.properties`의 `GEMINI_API_KEY=` → `BuildConfig.GEMINI_API_KEY`

> 원안은 Claude API + Tool Use였다. 이 레포는 **Gemini Flash + `responseSchema`** 로 동일한 “구조화 출력” 패턴을 쓴다. 아래 §10~11의 Claude/tool 예시는 **스키마·파이프라인 개념** 설명용이다.

---

## 1. 프로젝트 개요

### 1안 (현재 스코프) — 광고 화면 판별 + 닫기/스킵 위치 안내

- 지금 화면이 **전면 광고**인지 판별
- **X 닫기** 버튼이 있는지, 있다면 대략적 위치
- **스킵** 표시가 있는지, 있다면 대략적 위치
- **제외:** "몇 초 더 기다려야 하는지" — 화면에 명시적 숫자가 없으면 답할 수 없음
- **자동 클릭/자동 종료 없음** — 음성 안내까지만
- **광고 리다이렉트 차단 없음** — 브라우저·Play Store·설치 앱(AliExpress 등)으로 넘어가는 것은 **막지 않음**. 대신 **감지 + TTS**로 「뒤로가기로 게임으로」 안내 (자동 뒤로가기 없음)

### 2안 (향후 확장) — 화면 설명 및 조작 안내

- 화면 구성 설명: 버튼/텍스트/구조를 음성으로 설명
- 목적 기반 안내: "이거 어떻게 해?" 질문에 관련 UI 요소의 이름과 위치 안내
- 자동 클릭 없음

두 기능 모두 **"AI가 모든 걸 한 번에 처리"하지 않고**, 코드가 할 수 있는 결정적 작업과 AI가 필요한 판단 작업을 **단계별로 분리**한다.

---

## 2. 데이터 소스 (기능별로 다름)

| 기능 | 1순위 데이터 | 보조 데이터 | 비고 |
|------|-------------|------------|------|
| **1안 (광고)** | 스크린샷 + 비전 LLM | 같은 앱 **세션**의 플레이 참조 스크린샷 0~2장 (임시, 영구 저장 없음) | 광고 콘텐츠는 접근성 라벨이 거의 없어 트리 활용 불가. 참조는 **classify 프롬프트 맥락**으로만 사용 (픽셀 비교 아님) |
| **2안 (화면 안내)** | AccessibilityNodeInfo 트리 | — | 라벨/역할/좌표가 구조화됨. WebView·커스텀 렌더링만 비전 보완 |

---

## 3. 파이프라인

### 3.1 1안 파이프라인 (광고)

| 단계 | 담당 | 내용 |
|------|------|------|
| 1. 스크린샷 캡처 | 코드 | 사용자 트리거 시 1장 캡처 |
| 1a. 세션·포그라운드 앱 | 코드 | `packageName` 조회. **세션 주인(게임)** 유지·삭제 규칙 (§3.4). 광고 리다이렉트용 **일시 목적지** 전환 시 세션 **유지** |
| 1b. 광고 리다이렉트 감지 (이벤트) | 코드 | 게임 → 브라우저/스토어/설치 앱 등 **일시 목적지** 포그라운드 시 TTS 안내. **버튼·캡처·classify 없음** |
| 2. 전처리 | 코드 | 화면 모서리 크롭 (작거나 위장된 아이콘 대비) |
| 3. 광고 여부 분류 | Vision LLM | `{is_ad: bool}` — 단일 목적 프롬프트. 참조 2장 이상이면 참조+현재 화면을 **한 요청**에 전달 (별도 유사도 API 없음) |
| 3b. 참조 축적 (조건부) | 코드 | 3단계가 `false`일 때만, 안전 규칙 통과 시 세션 참조에 JPEG 저장 (최대 2장) |
| 4. 아이콘 위치 탐지 (조건부) | Vision LLM | 3단계가 true일 때만. 4분면 enum |
| 5. 응답 조립 | 코드 | 구조화 결과 → 고정 템플릿 문장 |
| 6. TTS | 코드 | 음성 출력 |

### 3.2 2안 파이프라인 (화면 안내)

| 단계 | 담당 | 내용 |
|------|------|------|
| 1. 트리 캡처 | 코드 | 보이는/클릭 가능한 노드만 (라벨, role, bounds) 직렬화 |
| 2. 비전 보완 (조건부) | Vision LLM | 트리에 라벨이 부실한 영역만 |
| 3. 의도 매칭 | Text LLM | 사용자 질문 + 트리 → 후보 노드 선택 |
| 4. 검증 | 코드 | LLM이 고른 노드 id가 실제 트리에 존재하는지 확인 |
| 5. 문장 생성 | Text LLM 또는 템플릿 | 자연스러운 안내 문장 |
| 6. TTS | 코드 | 음성 출력 |

### 3.3 단계 분리 원칙

1. OS API가 구조화된 형태로 주는 작업(트리 캡처, 스크린샷, 크롭, TTS)은 **코드**가 담당
2. 한 번의 API 호출에는 **하나의 명확한 질문**만 (세션 참조 이미지는 classify 질문의 **맥락 입력**이지, 별도 API 단계가 아님)
3. 비용이 큰 단계(위치 탐지 등)는 앞 단계 결과에 따라 **조건부 실행**
4. 환각 위험이 있는 "선택/매칭"과 "자연어 표현" 사이에 **코드 레벨 검증** 삽입
5. **세션 참조·하단 내비 바**는 약한 힌트다. 참조만으로 `is_ad=true`로 **뒤집지 않는다**. 참조는 `is_ad=false` 판정 보조·classify 맥락에만 쓴다.
6. **Intent 차단·자동 뒤로가기 금지** — 다른 앱의 광고 SDK가 여는 브라우저/스토어는 AccessibilityService로 **예방 불가**. `performGlobalAction(BACK)` 등 자동 복귀도 하지 않는다 (오동작·설계 원칙).

### 3.4 세션 플레이 참조 (GameplaySessionStore)

게임마다 영구 등록하지 않는다. **게임 세션 주인(`sessionOwnerPackage`)** 이 살아 있는 동안만 임시 JPEG를 둔다.

| 항목 | 규칙 |
|------|------|
| 저장 위치 | `cacheDir/gameplay_sessions/<sessionOwnerPackage>/` (git 없음) |
| 세션 주인 | 사용자가 **게임**을 포그라운드로 두면 그 `packageName`을 `sessionOwnerPackage`로 기록 |
| 삭제 시점 | **홈(런처)** · **다른 게임/앱**(일시 목적지 제외) 포그라운드 · 세션 **타임아웃**(예: 30분, owner 미복귀) · 서비스 `onDestroy` → `clearSession` / `clearAll()` |
| **유지** (삭제 안 함) | owner → **일시 목적지**(§3.5) → **BACK**으로 owner 복귀. 광고 클릭·자동 리다이렉트로 Chrome/Play Store/AliExpress 등에 잠깐 나갔다 돌아와도 **참조 0~2장 유지** |
| 참조 추가 | `classify` 결과 `is_ad=false` **이후에만**. 세션 **첫 버튼**은 저장하지 않음 |
| 참조 사용 | 세션에 **2장 이상**일 때만 classify 요청에 포함. 0~1장이면 현재 화면 1장만 |
| 최대 장수 | 2장, FIFO |
| 저장 간격 | 같은 세션에서 연속 저장 시 최소 3~5초 간격 |

**왜 “앱 전환 = 즉시 삭제”가 아닌가:** 게임 광고는 오탭·자동 이동으로 **브라우저/스토어/설치 앱**을 연다. OS 입장에선 앱 전환이지만 사용자는 **게임을 그만둔 게 아니다**. 이때 세션을 지우면 BACK 복귀 후 **워밍업부터 다시** 해야 한다.

**잘못된 화면(홈·로딩)이 참조에 들어갈 때:** 참조가 맞지 않으면 효과만 줄어들고, 일반 classify로 동작한다. 최악(광고가 참조로 저장)을 막기 위해 **`is_ad=false`일 때만 저장**한다.

**사용자 권장 습관:** 로딩이 끝난 뒤 **캐릭터가 보이는 플레이 화면**에서 버튼을 한두 번 눌러 두면 정확도가 올라간다. **게임을 종료하거나 홈으로 나가면** 참조는 삭제된다. 광고 리다이렉트로 잠깐 나갔다 **뒤로가기로 복귀**하면 참조는 **유지**된다.

### 3.5 광고 리다이렉트 안내 (AdRedirectMonitor)

**목적:** 사용자가 “지금 뒤로가기로 게임으로 돌아가야 하나?”를 스스로 판별하기 어려운 상황을 **TTS로만** 돕는다. **예방·차단·자동 복귀는 하지 않는다.**

| 항목 | 규칙 |
|------|------|
| 트리거 | `onAccessibilityEvent` — `TYPE_WINDOW_STATE_CHANGED` 등으로 **포그라운드 `packageName` 변경** 감지 |
| 조건 | `sessionOwnerPackage`(게임) 포그라운드 → **일시 목적지** 포그라운드로 바뀜 |
| 일시 목적지 (예) | `com.android.chrome`, `com.android.vending`(Play Store), `com.alibaba.aliexpress`, 제조사 브라우저 등 — **코드 상수 목록**으로 관리·추가 가능 |
| TTS (고정 템플릿) | **「브라우저나 스토어로 이동했을 수 있습니다. 뒤로가기를 눌러 게임으로 돌아가세요.」** (`SpokenMessageBuilder` / `strings.xml`) |
| 스팸 방지 | 같은 **리다이렉트 에피소드**당 1회 (debounce). `QUEUE_FLUSH` 가능 |
| 하지 않는 것 | Intent 가로채기, URL 차단, `performGlobalAction(BACK)`, 자동 클릭, 접근성 버튼 없이 classify |

**Custom Tabs / 인앱 WebView:** `packageName`이 **게임 그대로**일 수 있다. 이때는 리다이렉트 TTS가 **안 나올 수 있음** (한계). 사용자는 접근성 버튼으로 광고 분석은 계속 가능.

**세션과의 관계:** 리다이렉트 감지 시 `sessionOwnerPackage`·참조 JPEG는 **삭제하지 않음** (§3.4). owner 복귀 시 classify 맥락 유지.

---

## 4. 기술 선택

### 채택 (이 레포): Gemini Flash + JSON schema

- 클라이언트가 앱 하나뿐이고 파이프라인 순서가 고정 → 앱이 단계마다 API 직접 호출
- `responseMimeType=application/json` + `responseSchema`로 구조화 출력 강제
- 자연어 파싱 금지, 스키마 필드만 읽음

### 원안 참고: Claude API + Tool Use

- Tool Use / `tool_choice`는 이 프로젝트의 Gemini JSON schema와 **동일한 역할** (구조화 출력)
- §10의 tool 스키마는 개념 대응용

### 불필요: MCP, Agent SDK

| 옵션 | 판단 |
|------|------|
| MCP | 클라이언트 1개라 재사용 이득 없음. 다른 도구에서도 쓸 때 고려 |
| Agent SDK | 분기가 고정적이라 if/else로 충분. 프레임워크만 복잡도 증가 |

### 인증

- 개인 단일 사용자 → OAuth/서버 불필요
- API 키를 앱에 직접 사용 (`local.properties` → `BuildConfig`)
- 필요 시 얇은 개인용 프록시로 키 노출 방지 가능

---

## 5. 아키텍처

```
Android 앱 (트리거: 접근성 버튼)
   │
   ├─ 세션 (코드): sessionOwnerPackage · 임시 플레이 참조 0~2장 (홈/다른 게임/타임아웃 시 삭제)
   │
   ├─ 리다이렉트 (코드, 이벤트): 게임→일시 목적지 → TTS 「뒤로가기로 게임으로」 (세션 유지)
   │
   ├─ 캡처 (코드): takeScreenshot() 또는 AccessibilityNodeInfo 트리
   │
   ├─ Gemini API (단계별 분리, JSON schema)
   │     └─ classify: 참조 2장+ 있으면 맥락 이미지 포함
   │
   ├─ 코드 검증/조립 (환각 방지, 조건부 분기)
   │
   └─ TTS (코드)
```

---

## 6. Android Studio 프로젝트 설정

### 새 프로젝트 마법사

| 항목 | 값 |
|------|-----|
| Template | **Empty Activity** |
| Language | **Kotlin** |
| Minimum SDK | **API 30 (Android 11)** — `takeScreenshot()` 필수 |
| Build configuration | Kotlin DSL (`build.gradle.kts`) 권장 |

**minSdk 30 이유:** API 30 미만은 MediaProjection이 필요해 앱 실행마다 화면 캡처 허용 다이얼로그가 다시 뜸. API 30+ `takeScreenshot()`은 접근성 서비스 등록 한 번으로 온디맨드 캡처 가능.

### AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### build.gradle(:app) 의존성

```kotlin
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // JSON은 org.json (Android 내장) 사용
}
```

### API 키 설정

`local.properties` (git에 커밋하지 않음):

```properties
GEMINI_API_KEY=your-key-here
```

`build.gradle.kts`에서 `BuildConfig.GEMINI_API_KEY`로 주입.

---

## 7. AccessibilityService 등록

### res/xml/accessibility_service_config.xml

```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="true"
    android:canTakeScreenshot="true"
    android:accessibilityFlags="flagRequestAccessibilityButton"
    android:notificationTimeout="100" />
```

| 속성 | 역할 |
|------|------|
| `canTakeScreenshot` | `takeScreenshot()` 호출 허용 (API 30+) |
| `flagRequestAccessibilityButton` | 내비게이션 바 접근성 버튼 표시 → `AccessibilityButtonController` 콜백 호출 |
| `canRetrieveWindowContent` | 2안 트리 읽기용 (1안에서도 설정해 두면 확장 용이) |

### AndroidManifest.xml 서비스 등록

```xml
<service
    android:name=".AdBlockerAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

**접근성 권한:** 설치 후 **설정 > 접근성**에서 수동 1회 활성화 필요 (OS 정책, 코드로 자동 활성화 불가). `Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)`로 설정 화면까지는 앱에서 열 수 있음.

---

## 8. 트리거 — 접근성 버튼

```kotlin
class AdBlockerAccessibilityService : AccessibilityService() {
  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // 분석·캡처·classify 자동 실행 없음.
    sessionStore.onAccessibilityEvent(event)   // 세션 owner·삭제·유지
    adRedirectMonitor.onAccessibilityEvent(event)  // 게임→일시 목적지 TTS
  }
  override fun onInterrupt() {}

  // onAccessibilityButtonClicked()는 override 대상이 아니다.
  // 버튼은 AccessibilityButtonController에 콜백을 등록해서 받는다.
  override fun onServiceConnected() {
    super.onServiceConnected()
    accessibilityButtonController.registerAccessibilityButtonCallback(
      object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController) {
          captureAndAnalyze()
        }
      },
      Handler(Looper.getMainLooper()),
    )
  }
}
```

콜백은 `onDestroy`에서 `unregisterAccessibilityButtonCallback`으로 해제한다.

**왜 접근성 버튼인가:** `SYSTEM_ALERT_WINDOW`(오버레이) 권한 없이 트리거 가능. 플로팅 버튼 UI도 불필요.

**`onAccessibilityEvent` 예외:** 캡처·classify·광고 위치 TTS는 **접근성 버튼 온디맨드**만. 이벤트는 (1) **세션 owner·삭제/유지** (`GameplaySessionStore`), (2) **광고 리다이렉트 안내 TTS** (`AdRedirectMonitor`)에만 쓴다. 자동 스크린샷·자동 classify는 하지 않는다.

---

## 9. 스크린샷 캡처 + 전처리

```kotlin
private fun captureAndAnalyze() {
    takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor,
        object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val hwBitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                val bitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                result.hardwareBuffer.close()  // 메모리 누수 방지 — 필수
                bitmap?.let { runPipeline(it) }
            }
            override fun onFailure(errorCode: Int) {
                speak("이 화면은 캡처할 수 없습니다.")
            }
        })
}
```

| 단계 | 이유 |
|------|------|
| `wrapHardwareBuffer` | GPU 메모리 → Bitmap 브릿지 |
| `.copy(ARGB_8888)` | 하드웨어 비트맵은 `compress()` 불가 → 소프트웨어 비트맵으로 복사 |
| `hardwareBuffer.close()` | 네이티브 리소스 해제 |
| `onFailure` 처리 | FLAG_SECURE 앱(은행 등)에서 캡처 실패 |

### 모서리 크롭

```kotlin
fun cropCorner(bitmap: Bitmap, right: Boolean, bottom: Boolean, fraction: Float = 0.3f): Bitmap {
    val w = (bitmap.width * fraction).toInt()
    val h = (bitmap.height * fraction).toInt()
    val x = if (right) bitmap.width - w else 0
    val y = if (bottom) bitmap.height - h else 0
    return Bitmap.createBitmap(bitmap, x, y, w, h)
}
```

작은 X 버튼이 전체 화면 축소 시 뭉개지는 것을 방지. 4모서리 각각 크롭해 locate API에 함께 전달.

### Base64 인코딩

```kotlin
fun Bitmap.toBase64Jpeg(quality: Int = 85): String {
    val stream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}
```

`NO_WRAP`: 줄바꿈 없이 인코딩 (JSON 파싱 오류 방지).

---

## 10. Vision LLM — 구조화 출력 스키마

비전 LLM은 **정확한 픽셀 좌표**보다 **구역(4분면) 분류**가 신뢰도 높음. 자동 클릭이 없으므로 구역 정보만으로 충분.

이 레포에서는 Gemini `responseSchema`로 아래와 동일한 형태를 강제한다. (원안 Claude tool 스키마와 1:1 대응)

### 광고 판별 (classify)

```json
{
  "type": "object",
  "properties": { "is_ad": { "type": "boolean" } },
  "required": ["is_ad"]
}
```

### 아이콘 위치 (locate, 조건부 호출)

```json
{
  "type": "object",
  "properties": {
    "close_button": {
      "type": "string",
      "enum": ["top-left","top-right","bottom-left","bottom-right","not_found"]
    },
    "skip_indicator": {
      "type": "string",
      "enum": ["top-left","top-right","bottom-left","bottom-right","not_found"]
    }
  },
  "required": ["close_button", "skip_indicator"]
}
```

### 원안 Claude tool (참고)

```json
{
  "name": "classify_ad_screen",
  "input_schema": {
    "type": "object",
    "properties": { "is_ad": { "type": "boolean" } },
    "required": ["is_ad"]
  }
}
```

---

## 11. API 호출 패턴 (OkHttp + org.json)

```kotlin
suspend fun classifyAdScreen(
  currentImageB64: String,
  referenceImageB64s: List<String> = emptyList(),  // 0~2장. 2장 미만이면 API에 넣지 않음
  apiKey: String,
): Boolean = withContext(Dispatchers.IO) {
    // POST generateContent (Gemini)
    // 참조 있음: 이미지 파트 = 참조 N장 + current 1장
    // responseMimeType=application/json, responseSchema → is_ad 파싱
}
```

- `withContext(Dispatchers.IO)`: 메인 스레드 ANR 방지
- 자연어 응답 파싱 금지 — JSON 스키마 필드만 읽음

---

## 12. TTS

```kotlin
class AdBlockerAccessibilityService : AccessibilityService(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts.language = Locale.KOREAN
    }
    fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "adblocker-utterance")
    }
}
```

`QUEUE_FLUSH`: 새 안내가 이전 안내를 즉시 끊고 재생 (버튼 재누름 시 최신 결과 우선).

**고정 TTS 예 (SpokenMessageBuilder / strings.xml):**

| 상황 | 문장 (예) |
|------|-----------|
| 광고 아님 | 지금은 광고 화면이 아닙니다. |
| 광고 + X 위치 | (4분면 템플릿) |
| 캡처 실패 | 이 화면은 캡처할 수 없습니다. |
| **광고 리다이렉트** | **브라우저나 스토어로 이동했을 수 있습니다. 뒤로가기를 눌러 게임으로 돌아가세요.** |

리다이렉트 문장은 모델이 생성하지 않는다.

---

## 13. 파이프라인 조립

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

private fun runPipeline(bitmap: Bitmap) {
    scope.launch {
        val pkg = rootInActiveWindow?.packageName
        sessionStore.onForegroundApp(pkg)

        val fullB64 = bitmap.toBase64Jpeg()
        val refs = sessionStore.referencesFor(pkg)  // 2장 미만이면 classify에 미포함
        val isAd = classifyAdScreen(fullB64, referenceImageB64s = refs, apiKey)
        if (!isAd) {
            sessionStore.maybeAddReference(pkg, fullB64)  // is_ad=false + 안전 규칙 통과 시만
            speak("지금은 광고 화면이 아닙니다.")
            return@launch
        }

        val corners = listOf(
            cropCorner(bitmap, right = false, bottom = false),
            cropCorner(bitmap, right = true,  bottom = false),
            cropCorner(bitmap, right = false, bottom = true),
            cropCorner(bitmap, right = true,  bottom = true)
        )
        val result = locateAdControls(fullB64, corners.map { it.toBase64Jpeg() }, apiKey)
        speak(buildSpokenMessage(result))
    }
}

override fun onDestroy() {
    scope.cancel()
    sessionStore.clearAll()
    tts.shutdown()
    super.onDestroy()
}
```

`isAd == false`이면 위치 탐지 API 호출 생략 → 비용·지연 절감. 세션 참조는 `is_ad=false` 확정 **후**에만 축적한다.

---

## 14. 소스 파일 구조

```
app/src/main/java/com/example/adblocker/
├── MainActivity.kt                    # 접근성 설정 화면 이동, API 키 누락 안내
├── AdBlockerAccessibilityService.kt   # 트리거 → 캡처 → 파이프라인 → TTS
├── GeminiApiClient.kt                 # classifyAdScreen(참조 optional), locateAdControls
├── GeminiConfig.kt                    # MODEL_ID
├── GeminiPrompts.kt                   # classify / locate placeholder
├── GameplaySessionStore.kt            # sessionOwner, 임시 플레이 참조, 일시 목적지 시 세션 유지
├── AdRedirectMonitor.kt               # 게임→일시 목적지 TTS (차단·자동 BACK 없음)
├── ImageUtils.kt                      # toBase64Jpeg, cropCorner
├── Models.kt                          # ScreenQuadrant enum, AdControlsResult
├── NavBarDetector.kt                  # 하단 내비 바 보임 (약한 힌트)
└── SpokenMessageBuilder.kt            # 구조화 결과 → 한국어 TTS 문장

app/src/main/res/xml/
└── accessibility_service_config.xml
```

단계별 구현·확인 방법은 [`IMPLEMENTATION_GUIDE.md`](IMPLEMENTATION_GUIDE.md)를 따른다.

---

## 15. 테스트 방법

1. **프롬프트 먼저 검증 (가장 중요)** — `scripts/eval_gemini_flash.py` + `testdata/`. Kotlin 빌드 사이클 없이 스키마·프롬프트 확정.
2. **라벨링된 테스트셋** — 정답(광고 여부, X/스킵 실제 구역) 라벨링 후 API 응답과 비교.
3. **FLAG_SECURE** — 은행 앱 등에서 `onFailure` 음성 안내 확인.
4. **접근성 버튼** — 실기기에서 내비게이션 바 버튼 노출·동작 확인 (제조사별 차이).
5. **종단 지연** — 버튼 클릭부터 TTS까지 시간 측정. 느리면 JPEG quality 축소 또는 경량 모델 검토.
6. **네트워크 실패** — 비행기 모드에서 "네트워크를 확인해주세요" 안내 확인.
7. **세션 참조** — 플레이 중 버튼 2회(`is_ad=false`) 후 참조 2장 축적 → classify 요청에 참조 포함 여부 확인. 홈/다른 게임 전환 후 `cacheDir` 세션 삭제 확인.
8. **광고 리다이렉트** — 게임 → Chrome/Play Store(또는 목록 앱) → TTS 안내 1회. BACK으로 게임 복귀 시 **세션 참조 유지** 확인. 홈 전환 시 세션 삭제 확인.

---

## 16. 실기기 사용 흐름

1. 앱 설치 → **접근성 설정 열기**
2. 설정 > 접근성 > **AdBlocker** 켜기
3. 광고가 나올 게임 실행
4. (권장) 로딩이 끝난 **플레이 화면**에서 접근성 버튼 1~2회 — 세션 참조 축적 (`is_ad=false`일 때만 저장)
5. 광고가 뜨면 내비게이션 바 **접근성 버튼** 탭
6. "화면을 분석 중입니다" → 결과 음성 안내
7. 사용자가 직접 닫기/스킵 버튼 탭
8. (광고 오탭 등) 브라우저/스토어로 넘어가면 **「뒤로가기로 게임으로」** TTS → BACK으로 복귀 시 **참조 유지**
9. 게임 종료·**홈**·다른 게임 전환 시 참조 자동 삭제 (다시 찍을 필요 있으면 4번 반복)

---

## 17. 향후 확장 — 자동 클릭 난이도 참고

| 단계 | 내용 | 난이도 |
|------|------|--------|
| A | TTS 안내만 (현재 1안) | ★★ |
| B | "X를 누를까요?" 확인 후 1회 클릭 | ★★★ |
| C | 광고 판별 후 정해진 1동작만 자동 (예: 오른쪽 위 1탭) | ★★★☆ |
| D | 클릭 → 화면 변화 확인 → 다음 동작 루프 | ★★★★★ |

자동 클릭 시 추가 과제:
- 4분면 → **픽셀 좌표** 변환 (`dispatchGesture`)
- 클릭 후 대기·재캡처·상태 머신 (최대 재시도, 타임아웃)
- 오동작 시 스토어 이동·유료 버튼 등 **되돌리기 어려운 결과**
- 광고 SDK마다 UI 패턴이 달라 케이스 폭발

**권장:** 1안 완성 → B(확인 후 1클릭) → C 순으로 확장. 2안에서는 트리에 "닫기" 노드가 있으면 `performAction(ACTION_CLICK)`이 좌표 탭보다 정확.

---

## 18. 모델 티어 분산 (선택)

| 단계 | 추천 |
|------|------|
| 광고 여부 (이진 분류) | 가벼운/빠른 모델 가능 |
| 아이콘 위치 탐지 | 더 강한 비전 모델 |
| 2안 의도 매칭 | 텍스트 모델 (비전 불필요) |

이 레포 기본값: `gemini-2.5-flash` (`GeminiConfig.MODEL_ID`).

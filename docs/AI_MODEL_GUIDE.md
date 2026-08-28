# AI 모델 가이드 — 초보자용 단계별 안내

> 이 문서는 **AdBlocker** 프로젝트에서 AI(비전 LLM)를 **어떻게 테스트하고, 어디에 설정하고, 어떤 모델을 쓸지** 정리한 가이드입니다.  
> 앱 코드에 Gemini 연동은 **이미 붙어 있습니다.** 지금 막힌 부분은 **프롬프트 튜닝**입니다.

**관련 문서**

| 문서 | 내용 |
|------|------|
| [`PROJECT.md`](PROJECT.md) | 전체 설계, 파이프라인, JSON 스키마 |
| [`IMPLEMENTATION_GUIDE.md`](IMPLEMENTATION_GUIDE.md) | Kotlin 구현·실기기 확인 체크리스트 (부록 H2 주석 해제 순서) |

---

## 0. 지금 이 프로젝트의 AI 상태 (한눈에 보기)

| 항목 | 현재 상태 | 위치 |
|------|-----------|------|
| API 키 주입 | ✅ | `local.properties` → `BuildConfig.GEMINI_API_KEY` |
| 모델 ID 상수 | ✅ | `GeminiConfig.kt` (`gemini-2.5-flash`) |
| API 호출 (앱) | ✅ | `GeminiApiClient.kt` — `classifyAdScreen`, `locateAdControls` |
| 데이터 모델 | ✅ | `Models.kt` — `ScreenQuadrant`, `AdControlsResult`, `GeminiApiError` |
| 파이프라인 연결 | ✅ | `AdBlockerAccessibilityService.runPipeline()` |
| **프롬프트 (앱)** | ⚠️ TODO placeholder | `GeminiPrompts.kt` — `"TODO: classify prompt"` 등 |
| **프롬프트 (PC 테스트)** | ✅ 실제 문구 있음 | `scripts/eval_gemini_flash.py` |
| 테스트 이미지 폴더 | ✅ 폴더만 있음 | `testdata/ads/`, `testdata/not_ads/` |

**핵심:** 접근성 버튼을 누르면 앱이 **실제로 Gemini API를 호출**합니다. 다만 `GeminiPrompts.kt`가 아직 `TODO:` 문자열이라 **판별·위치 결과를 믿을 수 없습니다.**  
→ **PC에서 `eval_gemini_flash.py`로 프롬프트를 먼저 검증**한 뒤, 확정된 문구를 `GeminiPrompts.kt`에 복사하세요.

```
[지금 할 일 — 프롬프트]              [이미 되어 있는 것 — 코드]
testdata/ 스크린샷 수집              GeminiApiClient + runPipeline
     ↓                                    ↓
eval_gemini_flash.py 튜닝      →    GeminiPrompts.kt에 복사
     ↓                                    ↓
정답률 OK?                         실기기 버튼 → TTS
```

---

## 1. 이 앱에서 AI가 하는 일

접근성 버튼을 누르면 `runPipeline()`이 실행됩니다.

1. 스크린샷 캡처 (`takeScreenshot`)
2. (세션 참조 2장 이상이면) **참조 이미지 + 현재 화면**으로 classify
3. `is_ad=true`이면 **전체 화면 + 4모서리 크롭**으로 locate
4. JSON 결과를 `SpokenMessageBuilder`가 한국어 TTS 문장으로 변환

| 단계 | AI 질문 | 출력 (JSON) |
|------|---------|-------------|
| **분류 (classify)** | 지금 화면이 전면 광고인가? | `{ "is_ad": true/false }` |
| **위치 (locate)** | 광고일 때 X·스킵이 어느 구역에 있나? | `{ "close_button": "top-right", "skip_indicator": "not_found" }` |

AI는 **음성 문장을 직접 만들지 않습니다.** JSON만 받고, 앱이 고정 템플릿으로 TTS를 읽습니다.

### 1-1. PC 테스트 스크립트와 앱의 차이 (중요)

| | `eval_gemini_flash.py` | 앱 (`GeminiApiClient`) |
|--|------------------------|-------------------------|
| classify 입력 | 현재 화면 **1장** | 참조 **0~2장** + 현재 1장 (2장 미만이면 1장만) |
| classify 프롬프트 | `CLASSIFY_PROMPT` | 참조 2장 이상이면 `CLASSIFY_WITH_REFERENCES_PROMPT` |
| locate 입력 | 전체 화면 **1장** | 전체 1장 + **4모서리 크롭** 4장 (총 5장) |
| API 키 전달 | URL 쿼리 `?key=` | HTTP 헤더 `x-goog-api-key` |
| 이미지 전처리 | 원본 파일 그대로 | `ImageUtils` — 최대 1280px, JPEG 85% |

→ Python에서 classify가 잘 되어도, **앱의 locate는 입력이 다릅니다.** locate 프롬프트는 Python 결과를 기본으로 하되, **「전체 + 모서리 5장」** 구조에 맞게 문구를 보강하는 것이 좋습니다.  
→ `CLASSIFY_WITH_REFERENCES_PROMPT`는 스크립트에 없으므로, AI Studio에서 참조 2장 + 현재 1장을 넣어 따로 검증하세요.

---

## 2. 준비물

### 2-1. Google AI Studio API 키 (무료로 시작 가능)

1. 브라우저에서 [Google AI Studio](https://aistudio.google.com/apikey) 접속
2. Google 계정으로 로그인
3. **Create API key** 클릭 → 키 복사

> API 키는 비밀번호입니다. GitHub에 올리지 마세요. 이 프로젝트는 `local.properties`에만 넣습니다.

### 2-2. Python 3 (테스트 스크립트용)

```powershell
python --version
```

`Python 3.10` 이상이면 됩니다. 추가 패키지 설치는 **필요 없습니다**.

### 2-3. API 키를 프로젝트에 넣기

프로젝트 **루트**의 `local.properties`:

```properties
GEMINI_API_KEY=여기에_복사한_키_붙여넣기
```

- Android 빌드 → `BuildConfig.GEMINI_API_KEY`
- Python 스크립트도 같은 파일에서 키를 읽음

키를 넣은 뒤 앱 경고 카드가 사라지려면 **재빌드**가 필요합니다.

---

## 3. AI 모델 테스트 — 단계별 따라하기

### 3-1단계: 테스트용 스크린샷 모으기

```
testdata/
├── ads/          ← 전면 광고 (is_ad = true)
├── not_ads/      ← 플레이, 메뉴, 상점 등 (is_ad = false)
└── labels.csv    ← (선택) 파일별 정답 라벨
```

- 게임마다 3~5장씩만 있어도 1차 테스트 가능
- 헷갈리는 케이스를 많이 넣을수록 좋음 (작은 배너, 상점 팝업, 로딩 화면 등)

### 3-2단계: (선택) 정답 라벨 CSV

`testdata/labels.example.csv`를 복사해 `testdata/labels.csv`로 저장합니다.

```csv
filename,game,is_ad,close_button,skip_indicator,notes
cookie_ad_01.png,cookie,true,top-right,not_found,전면 영상광고
cookie_play_01.png,cookie,false,not_found,not_found,실제 플레이 HUD
```

### 3-3단계: 분류(classify) 테스트

```powershell
cd C:\Users\86952\AndroidStudioProjects\AdBlocker
python scripts/eval_gemini_flash.py
```

**기대 출력 예시**

```
model=gemini-2.5-flash  n=8
cookie_ad_01.png                         OK    expected=True predicted=True
cookie_play_01.png                       OK    expected=False predicted=False
...
classify accuracy: 7/8
```

### 3-4단계: 위치(locate) 테스트

```powershell
python scripts/eval_gemini_flash.py --locate
```

앱의 locate는 **5장 입력**이므로, Python 결과만으로 100% 대응되지는 않습니다. 기본 감을 잡은 뒤 실기기에서 보완하세요.

### 3-5단계: 다른 모델로 비교

```powershell
python scripts/eval_gemini_flash.py --model gemini-2.5-flash-lite
$env:GEMINI_MODEL = "gemini-2.5-flash-lite"
python scripts/eval_gemini_flash.py
```

### 3-6단계: 확정 프롬프트를 앱에 반영

`app/src/main/java/com/example/adblocker/GeminiPrompts.kt`를 열고 placeholder를 교체합니다.

```kotlin
object GeminiPrompts {
    const val CLASSIFY_PROMPT = """... eval 스크립트와 동일한 문자열 ..."""

    const val CLASSIFY_WITH_REFERENCES_PROMPT = """
        ... 참조 2장은 gameplay(광고 아님), 마지막 1장을 판별 ...
        (AI Studio에서 3장 테스트 후 작성)
    """

    const val LOCATE_PROMPT = """
        ... eval의 LOCATE_PROMPT + 전체/모서리 5장 구조 설명 ...
    """
}
```

저장 → **앱 재빌드·재설치** → 실기기에서 버튼 테스트.

**권장 워크플로**

1. `testdata/`에 이미지 10장+ → classify만 실행
2. 정확도 80% 미만 → `eval_gemini_flash.py`의 `CLASSIFY_PROMPT` 수정
3. `--locate`로 위치 감 잡기
4. `GeminiPrompts.kt`에 복사 (+ `CLASSIFY_WITH_REFERENCES_PROMPT` 별도 작성)
5. 실기기에서 파이프라인 확인 (9장 참고)

---

## 4. 프롬프트는 어디에 넣나?

| 용도 | 파일 | 상수 |
|------|------|------|
| **PC에서 빠른 실험** | `scripts/eval_gemini_flash.py` | `CLASSIFY_PROMPT`, `LOCATE_PROMPT` |
| **앱에서 실제 호출** | `GeminiPrompts.kt` | `CLASSIFY_PROMPT`, `CLASSIFY_WITH_REFERENCES_PROMPT`, `LOCATE_PROMPT` |

### 왜 Python에 먼저?

- Android 빌드·설치 없이 **몇 초 만에** 재실행
- 같은 이미지로 **여러 프롬프트·모델** 비교
- `TODO:` placeholder 상태로 실기기만 돌리면 TTS가 의미 없음

### 동기화 규칙

1. Python에서 만족스러운 `CLASSIFY_PROMPT` / `LOCATE_PROMPT` → `GeminiPrompts.kt`에 복사
2. 두 파일의 **문자열을 동일하게** 유지 (드리프트 방지)
3. `CLASSIFY_WITH_REFERENCES_PROMPT`는 앱 전용 — AI Studio 또는 수동 3장 테스트로 작성

### 앱이 이미지에 붙이는 라벨 (`GeminiApiClient.kt`)

모델이 순서를 알 수 있도록 텍스트 라벨이 함께 전송됩니다.

| 라벨 | 용도 |
|------|------|
| `image: reference gameplay screen (not an ad)` | 참조 스크린샷 (최대 2장) |
| `image: current screen to classify` | classify 대상 |
| `image: full screen` | locate 전체 화면 |
| `image: top-left/right corner crop` 등 | locate 4모서리 |

locate 프롬프트에는 **「첫 이미지는 전체, 이후 4장은 각 모서리 확대」** 를 명시하는 것이 좋습니다.

---

## 5. 모델 ID는 어디에 넣나?

| 용도 | 파일 / 방법 | 예시 |
|------|-------------|------|
| **앱** | `GeminiConfig.kt` | `const val MODEL_ID = "gemini-2.5-flash"` |
| **PC 테스트** | `--model` 또는 `GEMINI_MODEL` 환경 변수 | `gemini-2.5-flash` |

앱의 실제 호출 URL (`GeminiApiClient.kt`):

```
https://generativelanguage.googleapis.com/v1beta/models/{MODEL_ID}:generateContent
```

인증: 요청 헤더 `x-goog-api-key: <BuildConfig.GEMINI_API_KEY>`

`GeminiConfig.MODEL_ID` 한 곳만 바꾸면 앱 전체 모델이 바뀝니다.

---

## 6. 모델을 바꾸고 싶을 때

### 6-1. PC에서 비교 (먼저)

```powershell
python scripts/eval_gemini_flash.py --model gemini-2.5-flash-lite
python scripts/eval_gemini_flash.py --model gemini-2.5-flash
```

### 6-2. 앱 기본 모델 변경

`GeminiConfig.kt`:

```kotlin
object GeminiConfig {
    const val MODEL_ID = "gemini-2.5-flash-lite"
}
```

수정 후 **재빌드·재설치**.

### 6-3. 단계별 다른 모델 (고급)

classify / locate에 서로 다른 모델을 쓰려면 `GeminiConfig`에 역할별 상수를 두고 `GeminiApiClient.postGenerateContent`에 모델 ID 인자를 추가해야 합니다.  
현재 코드는 **하나의 `MODEL_ID`** 만 사용합니다. 초기에는 통일을 권장합니다.

---

## 7. 어떤 AI 모델을 쓰면 좋은가?

이 프로젝트는 **이미지 1~5장 + 짧은 JSON** 응답이므로 **Gemini Flash 계열**이 비용·속도·정확도 균형이 좋습니다.

> 가격·무료 한도는 Google 정책에 따라 바뀝니다. [Gemini API 가격](https://ai.google.dev/gemini-api/docs/pricing)에서 확인하세요.

### 7-1. 추천 순위 (개인용 · 무료 우선 · 건별 과금)

| 순위 | 모델 ID | 적합한 용도 | 장점 | 단점 |
|------|---------|-------------|------|------|
| **1 (기본)** | `gemini-2.5-flash` | classify + locate | 무료 티어, JSON schema 안정 | 어려운 케이스에서 가끔 오판 |
| **2 (저비용)** | `gemini-2.5-flash-lite` | classify 위주 | 가장 저렴 | locate 정확도↓ 가능 |
| **3 (정확도↑)** | `gemini-2.5-pro` | locate, Flash FAIL 많을 때 | 비전 추론 강함 | 느리고 비쌈 |

### 7-2. 다른 제공자

이 레포는 **Gemini REST + `responseSchema`** 기준입니다. OpenAI·Claude로 바꾸려면 `GeminiApiClient.kt` 전체를 다시 작성해야 합니다.

### 7-3. 비용 감각

버튼 1회 ≈ classify 1회 + (광고일 때) locate 1회.

- 개인이 하루 수십 번 사용 → Flash 계열이면 **월 소액**인 경우가 많음
- 개발·튜닝은 **무료 티어**로 대부분 가능
- 이미지 크기는 `ImageUtils.kt` (`MAX_LONG_EDGE = 1280`, `JPEG_QUALITY = 85`)로 이미 제한됨

---

## 8. 프롬프트를 어떻게 다듬나?

### 8-1. 자주 틀리는 패턴

| 실수 | 프롬프트에 추가할 내용 예시 |
|------|---------------------------|
| 인게임 상점 → 광고 | "In-game shop popup is NOT an ad" |
| 작은 배너 → 광고 | "Small banners not covering most of screen → is_ad=false" |
| 플레이 HUD → 광고 | "Gameplay HUD is NOT an ad" |
| 없는 X 지어냄 | "If not visible, use not_found" |

### 8-2. 수정 루프

```
FAIL 이미지 확인 → notes 기록 → eval 스크립트 프롬프트 수정
    → python scripts/eval_gemini_flash.py
    → OK면 GeminiPrompts.kt에 복사 → 앱 재빌드 → 실기기 확인
```

### 8-3. temperature

스크립트·앱 모두 **`temperature: 0`** (`GeminiApiClient`의 `generationConfig`).

---

## 9. 실기기에서 AI 동작 확인하기

코드(4~7단계)는 이미 연결되어 있습니다. **프롬프트를 `GeminiPrompts.kt`에 넣은 뒤** 아래를 확인하세요.

| 확인 항목 | 기대 동작 |
|-----------|-----------|
| 키 없음 | 「API 키가 없어 분석할 수 없습니다」 (캡처·API 호출 안 함) |
| 일반 화면 | 「화면을 분석 중입니다」 → 「지금은 광고 화면이 아닙니다」 |
| 세션 첫 버튼 | 「플레이 화면에서 접근성 버튼을 한 번 더 눌러 주세요」 (참조 저장 안 됨) |
| 두 번째 버튼부터 | `is_ad=false`일 때 참조 JPEG 저장 (최대 2장, 4초 간격) |
| 광고 화면 | 「전면 광고입니다. 닫기 버튼은 … 스킵은 …」 |
| 비행기 모드 | 「네트워크를 확인해 주세요」 |
| 연속 탭 | 이전 분석 취소, **마지막 결과만** TTS (`QUEUE_FLUSH`) |

**Logcat 필터:** `AdBlockerA11y`, `AdBlockerGemini`, `AdBlockerGameSession`

```
pipeline_result=PipelineResult(packageName=..., navBarVisible=..., referenceCount=..., isAd=..., controls=...)
```

> 프롬프트가 `TODO:`인 동안은 **파이프라인이 끝까지 도는지**, **실패 시 문장이 맞는지**만 보세요. 정답률은 프롬프트 튜닝 이후에 평가합니다.  
> 상세 체크리스트: [`IMPLEMENTATION_GUIDE.md` 부록 H2 S5](IMPLEMENTATION_GUIDE.md#s5--7-run-8-cancel-마지막)

---

## 10. 설정 파일 맵 (전체)

```
AdBlocker/
├── local.properties                    # GEMINI_API_KEY=
├── scripts/eval_gemini_flash.py        # PC 테스트 프롬프트 + CLI
├── testdata/                           # 스크린샷 + labels.csv
└── app/src/main/java/com/example/adblocker/
    ├── GeminiConfig.kt                 # MODEL_ID
    ├── GeminiPrompts.kt                # ⚠️ 프롬프트 (TODO → 실제 문구로 교체)
    ├── GeminiApiClient.kt              # REST 호출, JSON schema
    ├── Models.kt                       # ScreenQuadrant, GeminiApiError
    ├── ImageUtils.kt                   # JPEG/Base64/모서리 크롭
    ├── GameplaySessionStore.kt         # 세션 참조 0~2장
    ├── SpokenMessageBuilder.kt         # JSON → TTS 문장
    └── AdBlockerAccessibilityService.kt  # runPipeline
```

---

## 11. 자주 나는 문제

| 증상 | 원인 | 해결 |
|------|------|------|
| `GEMINI_API_KEY missing` | 키 미설정 | `local.properties`에 키 추가 |
| HTTP 400 / invalid model | 모델명 오타 | [모델 목록](https://ai.google.dev/gemini-api/docs/models) 확인 |
| HTTP 429 | 무료 한도 초과 | 대기, 유료 티어, 호출 줄이기 |
| `No images found` | testdata 비어 있음 | `ads/`, `not_ads/`에 이미지 추가 |
| 앱 경고 카드 유지 | BuildConfig 미갱신 | Clean + Rebuild |
| 항상 「분석에 실패했습니다」 | 스키마/프롬프트 오류 | Logcat `AdBlockerGemini`; PC에서 `eval` 재현 |
| TTS 내용이 이상함 | `GeminiPrompts.kt`가 TODO | Python에서 검증한 프롬프트로 교체 |
| Python OK, 앱 FAIL | 프롬프트·모델 불일치 | 두 파일 문자열·`MODEL_ID` 동기화 |
| 참조가 안 쌓임 | 세션 첫 버튼 / `is_ad=true` | 두 번째 버튼부터, `is_ad=false`일 때만 저장 |
| 연타 시 결과 섞임 | cancel 미동작 | `analysisJob?.cancel()` 확인 (이미 구현됨) |

---

## 12. 체크리스트

### A. 프롬프트 튜닝 (지금 할 일)

- [ ] `local.properties`에 `GEMINI_API_KEY`
- [ ] `testdata/`에 광고·비광고 이미지 각 5장+
- [ ] `python scripts/eval_gemini_flash.py` classify 목표 정확도
- [ ] `--locate` + `labels.csv`로 위치 감 확인
- [ ] `GeminiPrompts.kt`에 실제 프롬프트 반영 (3개 상수)
- [ ] `CLASSIFY_WITH_REFERENCES_PROMPT` 별도 검증

### B. 앱 동작 (코드는 이미 연결됨)

- [ ] 키 있을 때 버튼 → API 호출 + TTS
- [ ] 키 없음 / 비행기 모드 → 크래시 없이 안내
- [ ] Logcat `pipeline_result` 확인
- [ ] 참조 2장 축적 후 classify에 참조 포함

---

## 13. 한 줄 요약

1. **앱은 이미 Gemini를 호출**하지만, **프롬프트가 TODO**라 결과를 믿으면 안 됩니다.  
2. **튜닝은 PC에서** `eval_gemini_flash.py` + `testdata/`로 먼저 합니다.  
3. 확정 후 **`GeminiPrompts.kt`에 복사**하고 재빌드합니다.  
4. **모델**은 `GeminiConfig.kt` (앱) / `--model` (PC)에서 바꿉니다.  
5. **기본 모델:** `gemini-2.5-flash` — 비용 줄이면 `flash-lite`, locate가 어려우면 `pro` 검토.

---

*마지막 갱신: 2026-08-27 — `GeminiApiClient`·`runPipeline` 연결 완료, `GeminiPrompts.kt` TODO placeholder 상태 반영*

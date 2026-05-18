# 🏃 Make Pace (러닝 코칭 앱)

Make Pace는 사용자의 실시간 러닝 데이터를 분석하여 최적의 페이스를 가이드하는 스마트 러닝 코칭 애플리케이션입니다. 나이키 런 클럽(NRC)의 세련된 UI를 모티브로 하며, 정밀한 위치 추적과 머신러닝 기반 분석을 목표로 합니다.

## 🚀 주요 기능

### 1. 정밀 실시간 러닝 추적
- **Fused Location Provider**: 백그라운드에서도 끊김 없는 위치 추적을 수행합니다.
- **Noise Filtering**: `LocationFilter`를 통해 GPS 오차를 보정하고 비현실적인 속도 튀는 현상을 걸러냅니다.
- **Elevation Gain**: 실시간 고도 변화를 감지하여 '총 획득 고도'를 계산합니다.
- **Pace Calculation**: 이동 거리와 시간의 상관관계를 분석하여 실시간 페이스(min/km)를 산출합니다.

### 2. NRC 스타일 UI/UX
- **Sea Blue 테마**: 바다를 닮은 파란색 테마로 디자인 일관성을 확보했습니다.
- **인터랙티브 컨트롤**:
    - **3초 카운트다운**: 시작 전 준비 시간을 제공하며, 4회 연타 시 취소 가능합니다.
    - **롱프레스 종료**: 실수로 인한 종료를 방지하기 위해 2초간 꾹 누르면 파도가 차오르는 모션과 함께 종료됩니다.
    - **재시작 방지 락**: 종료 후 3초간 락을 걸어 의도치 않은 재실행을 방지합니다.

### 3. 데이터 관리 및 분석 준비
- **오프라인 우선 (Room)**: 모든 러닝 경로와 시계열 데이터를 로컬 DB에 선 저장합니다.
- **클라우드 동기화 (Firebase)**: Firestore를 통해 좌표, 속도, 고도 데이터를 시계열로 저장하여 머신러닝 학습 데이터셋을 구축합니다.
- **활동 기록**: 과거 러닝 기록을 리스트로 확인하고, 상세 경로 지도 및 수치를 파악할 수 있습니다.
- **기록 공유/삭제**: 나만의 기록을 공유하거나 관리할 수 있는 기능을 제공합니다.

### 4. 실시간 보이스 코칭
- **TTS 엔진**: 설정된 페이스 전략(리커버리, 템포, 스피드 런)에 따라 실시간 음성 피드백을 제공합니다.

## 🛠 Tech Stack
- **Language**: Kotlin
- **Architecture**: MVVM, Clean Architecture (Modular UI)
- **Library**:
    - Jetpack (Navigation, Room, ViewModel, Lifecycle)
    - Google Maps SDK
    - Firebase (Firestore, Analytics)
    - Coroutines & Flow (실시간 데이터 처리)

## 📌 Setup
1. `local.properties` 파일에 `MAPS_API_KEY`를 추가해야 지도가 정상 작동합니다.
2. Firebase 연동을 위해 `google-services.json` 파일이 `app/` 폴더에 필요합니다.

---
*Developed by Senior Android Developer*

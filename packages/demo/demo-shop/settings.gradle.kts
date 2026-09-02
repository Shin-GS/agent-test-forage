rootProject.name = "demo-shop"

// 로컬 라이브러리를 composite build로 포함 (publish 없이 바로 참조).
// 라이브러리 수정 시 재빌드만으로 즉시 반영된다.
includeBuild("../../library/java/21")

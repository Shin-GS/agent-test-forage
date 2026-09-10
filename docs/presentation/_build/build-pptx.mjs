// 슬라이드 이미지들을 16:9 PPT로 조립 (여백 없이 꽉 채움)
import pptxgen from "pptxgenjs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import fs from "node:fs";

// __dirname = docs/competition/_build
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const competitionDir = path.join(__dirname, "..");
// 슬라이드 조각 이미지는 _build/slides 에 위치
const slidesDir = path.join(__dirname, "slides");

const pptx = new pptxgen();
// 16:9 와이드 (기본 10 x 5.625 인치)
pptx.defineLayout({ name: "W16x9", width: 13.333, height: 7.5 });
pptx.layout = "W16x9";

const files = fs.readdirSync(slidesDir)
  .filter(f => /^slide-\d+\.png$/.test(f))
  .sort((a, b) => parseInt(a.match(/\d+/)[0]) - parseInt(b.match(/\d+/)[0]));

for (const f of files) {
  const slide = pptx.addSlide();
  // 슬라이드 전체를 이미지로 채움 (여백 0)
  slide.addImage({
    path: path.join(slidesDir, f),
    x: 0, y: 0, w: 13.333, h: 7.5,
  });
}

const outPath = path.join(competitionDir, "테스트메이트-발표자료.pptx");
await pptx.writeFile({ fileName: outPath });
console.log("PPTX 생성 완료:", outPath, "(" + files.length + " slides)");

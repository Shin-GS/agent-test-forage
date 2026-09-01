/**
 * QA Test Checklist Renderer
 * 프로그레스 바 + 기능별 미니 진행률 + 컴팩트 모드 + 시간 표시
 */

const STORAGE_KEY = "qa-test-results";

class TestRenderer {
  constructor(containerId) {
    this.container = document.getElementById(containerId);
    this.testModules = [];
    this.currentFeature = null;
    this.statusFilter = "all";
    this.compact = false;
    this.results = this.loadResults();
  }

  loadResults() {
    try {
      const data = localStorage.getItem(STORAGE_KEY);
      return data ? JSON.parse(data) : {};
    } catch { return {}; }
  }

  saveResults() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(this.results));
  }

  registerModule(module) {
    this.testModules.push(module);
  }

  updateStatus(caseId, status) {
    if (status === "pending") {
      delete this.results[caseId];
    } else {
      this.results[caseId] = { status, updatedAt: new Date().toISOString() };
    }
    this.saveResults();
    this.render();
  }

  resetCurrentFeature() {
    if (!this.currentFeature) return;
    const module = this.testModules.find(m => m.feature === this.currentFeature);
    if (module) {
      module.cases.forEach(c => delete this.results[c.id]);
      this.saveResults();
      this.render();
    }
  }

  resetAll() {
    if (confirm("모든 테스트 결과를 초기화하시겠습니까?")) {
      this.results = {};
      this.saveResults();
      this.render();
    }
  }

  getStatus(caseId) {
    return this.results[caseId]?.status || "pending";
  }

  getUpdatedAt(caseId) {
    return this.results[caseId]?.updatedAt || null;
  }

  getStats(cases) {
    const stats = { total: cases.length, pass: 0, fail: 0, skip: 0, pending: 0 };
    cases.forEach(c => { stats[this.getStatus(c.id)]++; });
    return stats;
  }

  /** 상대 시간 포맷 */
  formatRelativeTime(isoString) {
    if (!isoString) return "";
    const diff = Date.now() - new Date(isoString).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return "방금";
    if (mins < 60) return `${mins}분 전`;
    const hours = Math.floor(mins / 60);
    if (hours < 24) return `${hours}시간 전`;
    const days = Math.floor(hours / 24);
    if (days < 7) return `${days}일 전`;
    return new Date(isoString).toLocaleDateString("ko-KR", { month: "short", day: "numeric" });
  }

  /** 프로그레스 바 HTML */
  renderProgressBar(stats) {
    const { total, pass, fail, skip } = stats;
    const pctPass = total ? (pass / total * 100).toFixed(1) : 0;
    const pctFail = total ? (fail / total * 100).toFixed(1) : 0;
    const pctSkip = total ? (skip / total * 100).toFixed(1) : 0;
    const completed = pass + fail + skip;
    const pctCompleted = total ? Math.round(completed / total * 100) : 0;

    return `
      <div class="progress-bar-container">
        <div class="progress-bar-label">
          <span>전체 진행: ${pctCompleted}% (${completed}/${total})</span>
          <span>Pass ${pass} · Fail ${fail} · Skip ${skip} · 대기 ${stats.pending}</span>
        </div>
        <div class="progress-bar">
          <div class="progress-bar__pass" style="width: ${pctPass}%"></div>
          <div class="progress-bar__fail" style="width: ${pctFail}%"></div>
          <div class="progress-bar__skip" style="width: ${pctSkip}%"></div>
        </div>
      </div>
    `;
  }

  /** 기능별 미니 카드 */
  renderFeatureList() {
    return `
      <div class="feature-list">
        ${this.testModules.map(m => {
          const s = this.getStats(m.cases);
          const pct = s.total ? Math.round((s.pass + s.fail + s.skip) / s.total * 100) : 0;
          const pctPass = s.total ? (s.pass / s.total * 100).toFixed(1) : 0;
          const pctFail = s.total ? (s.fail / s.total * 100).toFixed(1) : 0;
          const pctSkip = s.total ? (s.skip / s.total * 100).toFixed(1) : 0;
          const active = m.feature === this.currentFeature;
          return `
            <div class="feature-item ${active ? "active" : ""}" data-feature="${m.feature}">
              <div class="feature-item__name">${m.screen}</div>
              <div class="feature-item__stats">${s.pass}/${s.total} Pass · ${pct}% 진행</div>
              <div class="feature-item__bar">
                <div class="progress-bar__pass" style="width: ${pctPass}%"></div>
                <div class="progress-bar__fail" style="width: ${pctFail}%"></div>
                <div class="progress-bar__skip" style="width: ${pctSkip}%"></div>
              </div>
            </div>
          `;
        }).join("")}
      </div>
    `;
  }

  render() {
    const module = this.testModules.find(m => m.feature === this.currentFeature);
    const allCases = this.testModules.flatMap(m => m.cases);
    const globalStats = this.getStats(allCases);

    let html = "";

    // 헤더
    html += `
      <a class="back-link" href="../index.html">← 문서 포털</a>
      <div class="app-header">
        <h1>🧪 QA Test Checklist</h1>
        <div class="stats">
          <span class="stat-badge pass">✅ ${globalStats.pass}</span>
          <span class="stat-badge fail">❌ ${globalStats.fail}</span>
          <span class="stat-badge skip">⏭️ ${globalStats.skip}</span>
          <span class="stat-badge pending">⬜ ${globalStats.pending}</span>
        </div>
      </div>
    `;

    // 프로그레스 바
    html += `<div class="progress-section">`;
    html += this.renderProgressBar(globalStats);
    html += `</div>`;

    // 기능별 미니 카드
    html += this.renderFeatureList();

    // 컨트롤
    html += `
      <div class="controls">
        <select id="status-filter">
          <option value="all" ${this.statusFilter === "all" ? "selected" : ""}>상태: 전체</option>
          <option value="pending" ${this.statusFilter === "pending" ? "selected" : ""}>⬜ 미진행</option>
          <option value="pass" ${this.statusFilter === "pass" ? "selected" : ""}>✅ Pass</option>
          <option value="fail" ${this.statusFilter === "fail" ? "selected" : ""}>❌ Fail</option>
          <option value="skip" ${this.statusFilter === "skip" ? "selected" : ""}>⏭️ Skip</option>
        </select>
        <button class="btn-compact ${this.compact ? "active" : ""}" id="btn-compact">컴팩트</button>
        <button class="btn-reset" onclick="renderer.resetCurrentFeature()">현재 기능 초기화</button>
        <button class="btn-reset" onclick="renderer.resetAll()">전체 초기화</button>
      </div>
    `;

    // 현재 기능 표시
    if (module) {
      const modStats = this.getStats(module.cases);
      html += `<div style="margin-bottom: 12px; font-size: 0.85rem; color: var(--color-primary); font-weight: 600;">
        📂 ${module.screen} (${modStats.pass}/${modStats.total} Pass)
        <button class="btn-reset" style="margin-left: 8px; font-size: 0.7rem; padding: 2px 8px;" data-feature="">← 전체 보기</button>
      </div>`;
    }

    // 케이스 목록
    const displayCases = module ? module.cases : allCases;
    const filteredCases = this.statusFilter === "all"
      ? displayCases
      : displayCases.filter(c => this.getStatus(c.id) === this.statusFilter);

    if (filteredCases.length === 0) {
      html += `<div class="empty-state"><p>표시할 테스트 케이스가 없습니다.</p></div>`;
    } else {
      html += `<div class="case-list ${this.compact ? "compact" : ""}">`;
      filteredCases.forEach(c => {
        const status = this.getStatus(c.id);
        html += this.renderCase(c, status);
      });
      html += `</div>`;
    }

    this.container.innerHTML = html;
    this.bindEvents();
  }

  renderCase(c, status) {
    const updatedAt = this.getUpdatedAt(c.id);
    const timeStr = updatedAt ? this.formatRelativeTime(updatedAt) : "";

    const stepsHtml = c.steps && c.steps.length > 0
      ? `<ol class="case-steps">${c.steps.map(s => `<li>${s}</li>`).join("")}</ol>`
      : "";

    return `
      <div class="case-card status-${status}" data-id="${c.id}">
        <div class="case-header">
          <span class="case-id">${c.id}</span>
          ${timeStr ? `<span class="case-time">${timeStr}</span>` : ""}
        </div>
        <div class="case-title">${c.title}</div>
        ${c.precondition ? `<div class="case-detail"><strong>전제:</strong> ${c.precondition}</div>` : ""}
        ${stepsHtml}
        ${c.expected ? `<div class="case-expected">✅ 기대: ${c.expected}</div>` : ""}
        <div class="status-buttons">
          <button class="status-btn pass ${status === "pass" ? "active" : ""}" data-id="${c.id}" data-status="pass">Pass</button>
          <button class="status-btn fail ${status === "fail" ? "active" : ""}" data-id="${c.id}" data-status="fail">Fail</button>
          <button class="status-btn skip ${status === "skip" ? "active" : ""}" data-id="${c.id}" data-status="skip">Skip</button>
        </div>
      </div>
    `;
  }

  bindEvents() {
    // 기능 카드 클릭
    document.querySelectorAll(".feature-item").forEach(item => {
      item.addEventListener("click", () => {
        const feature = item.dataset.feature;
        this.currentFeature = this.currentFeature === feature ? null : feature;
        this.render();
      });
    });

    // "전체 보기" 버튼
    document.querySelectorAll("[data-feature='']").forEach(btn => {
      btn.addEventListener("click", (e) => {
        e.stopPropagation();
        this.currentFeature = null;
        this.render();
      });
    });

    // 상태 필터
    const statusFilter = document.getElementById("status-filter");
    if (statusFilter) {
      statusFilter.addEventListener("change", (e) => {
        this.statusFilter = e.target.value;
        this.render();
      });
    }

    // 컴팩트 토글
    const btnCompact = document.getElementById("btn-compact");
    if (btnCompact) {
      btnCompact.addEventListener("click", () => {
        this.compact = !this.compact;
        this.render();
      });
    }

    // 상태 버튼
    document.querySelectorAll(".status-btn").forEach(btn => {
      btn.addEventListener("click", (e) => {
        const id = e.target.dataset.id;
        const newStatus = e.target.dataset.status;
        const currentStatus = this.getStatus(id);
        this.updateStatus(id, currentStatus === newStatus ? "pending" : newStatus);
      });
    });
  }
}

export default TestRenderer;

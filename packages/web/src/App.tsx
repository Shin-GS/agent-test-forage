function App() {
  return (
    <div className="flex h-full items-center justify-center">
      <div className="text-center">
        <div
          className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-2xl text-3xl"
          style={{ background: "var(--color-accent-subtle)" }}
        >
          ⚡
        </div>
        <h1 className="text-xl font-semibold" style={{ color: "var(--color-text-primary)" }}>
          AI Test Forge
        </h1>
        <p className="mt-2 text-sm" style={{ color: "var(--color-text-secondary)" }}>
          프로젝트 뼈대 — FE 스켈레톤
        </p>
      </div>
    </div>
  );
}

export default App;

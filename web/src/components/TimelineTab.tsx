import type {
  ResearchFindingRecord,
  ResearchIterationRecord,
  ResearchSourceRecord,
  ReportCitation
} from "../types";

export function TimelineTab({
  phaseSummary,
  findings,
  sources,
  citations,
  iterations,
  latestSources,
  setSelectedSourceId
}: {
  phaseSummary: { phase: string; iterationNo: number; summary: string };
  findings: ResearchFindingRecord[];
  sources: ResearchSourceRecord[];
  citations: ReportCitation[];
  iterations: ResearchIterationRecord[];
  latestSources: ResearchSourceRecord[];
  setSelectedSourceId: (id: string) => void;
}) {
  return (
    <div className="timeline-stepper" style={{ padding: "16px 0" }}>
      {iterations.length === 0 ? <div className="empty-card">暂无研究迭代记录。</div> : null}
      <div style={{ display: "flex", flexDirection: "column", gap: "0", position: "relative" }}>
        {iterations.length > 0 && <div style={{ position: "absolute", left: "15px", top: "24px", bottom: "24px", width: "2px", background: "var(--border)", zIndex: 0 }} />}
        {iterations.map((iteration, index) => (
          <div key={`${iteration.iterationNo}-${iteration.phase}`} style={{ display: "flex", gap: "16px", position: "relative", zIndex: 1, paddingBottom: index === iterations.length - 1 ? "0" : "32px" }}>
            <div style={{ width: "32px", height: "32px", borderRadius: "50%", background: "var(--bg-main)", border: "2px solid var(--ink-main)", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0, fontWeight: 600, fontSize: "0.85rem", color: "var(--ink-main)" }}>
              {iteration.iterationNo}
            </div>
            <div style={{ flex: 1, background: "var(--bg-hover)", padding: "16px", borderRadius: "var(--radius-md)", border: "1px solid var(--border)" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
                <span style={{ fontSize: "0.75rem", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.05em", color: "var(--ink-muted)" }}>{iteration.phase}</span>
              </div>
              <strong style={{ display: "block", fontSize: "1rem", marginBottom: "12px", color: "var(--ink-main)" }}>{iteration.summary}</strong>
              {iteration.confirmedFindings.length > 0 ? <div style={{ fontSize: "0.85rem", color: "var(--ink-muted)", marginBottom: "4px" }}><strong style={{ color: "var(--ink-main)" }}>Confirmed:</strong> {iteration.confirmedFindings.join(" · ")}</div> : null}
              {iteration.nextSearchIntent.length > 0 ? <div style={{ fontSize: "0.85rem", color: "var(--ink-muted)", marginBottom: "4px" }}><strong style={{ color: "var(--ink-main)" }}>Next:</strong> {iteration.nextSearchIntent.join(" · ")}</div> : null}
              {iteration.openQuestions.length > 0 ? <div style={{ fontSize: "0.85rem", color: "var(--ink-muted)" }}><strong style={{ color: "var(--ink-main)" }}>Gaps:</strong> {iteration.openQuestions.join(" · ")}</div> : null}
            </div>
          </div>
        ))}
      </div>
      {latestSources.length > 0 ? (
        <div className="stack-list" style={{ marginTop: "32px", paddingTop: "24px", borderTop: "1px solid var(--border)" }}>
          <strong style={{ fontSize: "0.95rem", marginBottom: "12px", display: "block" }}>Latest Sources</strong>
          <div className="source-chip-list">
            {latestSources.map((source) => (
              <button key={source.sourceId} className="source-chip source-chip-button" onClick={() => setSelectedSourceId(source.sourceId)}>
                <span className="source-chip-meta">
                  <strong className="source-chip-label">{source.title}</strong>
                  <span className="source-chip-host">{source.domain || source.kind}</span>
                </span>
              </button>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  );
}

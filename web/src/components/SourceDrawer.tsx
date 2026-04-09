import type { ResearchSourceRecord } from "../types";

export function SourceDrawer({
  source,
  findingTitles,
  usedInReport,
  onClose
}: {
  source: ResearchSourceRecord | null;
  findingTitles: string[];
  usedInReport: string[];
  onClose: () => void;
}) {
  if (!source) return null;
  return (
    <aside className="research-drawer">
      <div className="section-title-row">
        <strong>Source</strong>
        <button className="ghost-button" onClick={onClose}>关闭</button>
      </div>
      <div className="stack-list">
        <strong>{source.title}</strong>
        {source.uri ? <a href={source.uri} target="_blank" rel="noreferrer">{source.uri}</a> : null}
        <span>{source.domain || source.kind}</span>
        {source.evidenceStatus ? <span>Status: {source.evidenceStatus}</span> : null}
        {source.discoveryQuery ? <span>Query: {source.discoveryQuery}</span> : null}
        {source.verificationMethod ? <span>Verification: {source.verificationMethod}</span> : null}
        {source.snippet ? <p>{source.snippet}</p> : null}
        {findingTitles.length > 0 ? (
          <div className="source-chip-list">
            {findingTitles.map((title) => (
              <span key={title} className="source-chip">
                <span className="source-chip-meta">
                  <strong className="source-chip-label">{title}</strong>
                </span>
              </span>
            ))}
          </div>
        ) : null}
        {usedInReport.length > 0 ? (
          <div className="stack-list">
            <strong>Used In Report</strong>
            {usedInReport.map((placement) => (
              <span key={placement}>{placement}</span>
            ))}
          </div>
        ) : null}
      </div>
    </aside>
  );
}

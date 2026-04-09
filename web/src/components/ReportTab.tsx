import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type {
  ResearchReportView,
  ResearchSourceRecord,
  ReportCitation
} from "../types";

export function ReportTab({
  report,
  citationById,
  sourceById,
  setSelectedSourceId
}: {
  report: ResearchReportView | null;
  citationById: Record<string, ReportCitation>;
  sourceById: Record<string, ResearchSourceRecord>;
  setSelectedSourceId: (id: string) => void;
}) {
  return (
    <div className="stack-list">
      {!report ? <div className="empty-card">报告尚未生成，运行中会先展示计划和时间线。</div> : null}
      {report?.blocks.map((block) => {
        const blockCitations = block.citationIds.map((citationId) => citationById[citationId]).filter(Boolean) as ReportCitation[];
        const blockSources = blockCitations
          .map((citation) => sourceById[citation.sourceId])
          .filter(Boolean)
          .slice(0, 3) as ResearchSourceRecord[];
        const isUnverifiedBlock = block.text.includes("Unverified / Needs More Research");
        return (
          <article key={block.blockId} className={`message-bubble research-report-block ${isUnverifiedBlock ? "research-report-block-unverified" : ""}`}>
            <div className="markdown-body">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{block.text}</ReactMarkdown>
            </div>
            {blockCitations.length > 0 ? (
              <div className="source-chip-list">
                {blockCitations.map((citation) => (
                  <button key={citation.citationId} className="source-chip source-chip-button" onClick={() => setSelectedSourceId(citation.sourceId)}>
                    <span className="source-chip-index">{citation.citationLabel}</span>
                    <span className="source-chip-meta">
                      <strong className="source-chip-label">{citation.title}</strong>
                    </span>
                  </button>
                ))}
              </div>
            ) : null}
            {blockSources.length > 0 ? (
              <div className="source-chip-list">
                {blockSources.map((source) => (
                  <button key={source.sourceId} className="source-chip source-chip-button" onClick={() => setSelectedSourceId(source.sourceId)}>
                    <span className="source-chip-meta">
                      <strong className="source-chip-label">{source.title}</strong>
                      <span className="source-chip-host">{source.domain || source.kind}</span>
                    </span>
                  </button>
                ))}
              </div>
            ) : null}
          </article>
        );
      })}
    </div>
  );
}

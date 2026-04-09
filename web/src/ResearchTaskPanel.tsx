import { useMemo, useState } from "react";
import type {
  ReportCitation,
  ResearchFindingRecord,
  ResearchIterationRecord,
  ResearchReportSection,
  ResearchReportView,
  ResearchSourceRecord,
  TaskRecord
} from "./types";
import { SourceDrawer } from "./components/SourceDrawer";
import { TimelineTab } from "./components/TimelineTab";
import { ReportTab } from "./components/ReportTab";

type ResearchTaskPanelProps = {
  task: TaskRecord;
  plan: ResearchReportSection[];
  iterations: ResearchIterationRecord[];
  findings: ResearchFindingRecord[];
  report: ResearchReportView | null;
  sources: ResearchSourceRecord[];
  citations: ReportCitation[];
};

function summarizePhase(task: TaskRecord, iterations: ResearchIterationRecord[]) {
  const latestIteration = iterations[iterations.length - 1];
  return {
    phase: task.stage || latestIteration?.phase || "planning",
    iterationNo: latestIteration?.iterationNo || 0,
    summary: latestIteration?.summary || task.summary
  };
}

export default function ResearchTaskPanel({
  task,
  plan,
  iterations,
  findings,
  report,
  sources,
  citations
}: ResearchTaskPanelProps) {
  const [tab, setTab] = useState<"plan" | "timeline" | "report">("timeline");
  const [selectedSourceId, setSelectedSourceId] = useState<string | null>(null);
  const sourceById = useMemo(() => Object.fromEntries(sources.map((source) => [source.sourceId, source])), [sources]);
  const citationById = useMemo(() => Object.fromEntries(citations.map((citation) => [citation.citationId, citation])), [citations]);
  const selectedSource = selectedSourceId ? sourceById[selectedSourceId] || null : null;
  
  const selectedFindingTitles = useMemo(() => {
    if (!selectedSource) return [];
    const supported = new Set(selectedSource.supportingFindingIds || []);
    return findings.filter((finding) => supported.has(finding.findingId)).map((finding) => finding.title);
  }, [findings, selectedSource]);
  
  const selectedSourcePlacements = useMemo(() => {
    if (!selectedSource) return [];
    return citations
      .filter((citation) => citation.sourceId === selectedSource.sourceId && citation.usedInReport)
      .map((citation) => `${citation.citationLabel}${citation.paragraphId ? ` · ${citation.paragraphId}` : ""}`);
  }, [citations, selectedSource]);
  
  const latestSources = useMemo(
    () => [...sources]
      .sort((left, right) => (right.iterationNo || 0) - (left.iterationNo || 0))
      .slice(0, 6),
    [sources]
  );
  
  const phaseSummary = summarizePhase(task, iterations);

  return (
    <section className="research-task-panel">
      <div className="section-title-row">
        <div className="stack-list">
          <strong>{task.title}</strong>
          <span>{phaseSummary.phase} · 迭代 {phaseSummary.iterationNo || 0} · {task.progress ?? 0}%</span>
          <span>{phaseSummary.summary}</span>
        </div>
        <div className="mode-switch">
          <button className={tab === "plan" ? "active" : ""} onClick={() => setTab("plan")}>Plan</button>
          <button className={tab === "timeline" ? "active" : ""} onClick={() => setTab("timeline")}>Timeline</button>
          <button className={tab === "report" ? "active" : ""} onClick={() => setTab("report")}>Report</button>
        </div>
      </div>

      {tab === "plan" ? (
        <div className="stack-list">
          {plan.length === 0 ? <div className="empty-card">暂无研究计划。</div> : null}
          {plan.map((section) => (
            <div key={section.sectionId} className="resource-row">
              <strong>{section.title}</strong>
              <span>{section.summary}</span>
            </div>
          ))}
        </div>
      ) : null}

      {tab === "timeline" ? (
        <TimelineTab
          phaseSummary={phaseSummary}
          findings={findings}
          sources={sources}
          citations={citations}
          iterations={iterations}
          latestSources={latestSources}
          setSelectedSourceId={setSelectedSourceId}
        />
      ) : null}

      {tab === "report" ? (
        <ReportTab
          report={report}
          citationById={citationById}
          sourceById={sourceById}
          setSelectedSourceId={setSelectedSourceId}
        />
      ) : null}

      <SourceDrawer
        source={selectedSource}
        findingTitles={selectedFindingTitles}
        usedInReport={selectedSourcePlacements}
        onClose={() => setSelectedSourceId(null)}
      />
    </section>
  );
}

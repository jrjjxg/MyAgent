import { useEffect, useState } from "react";
import type { ResearchDraftRecord, ResearchPlanStep, UpdateResearchDraftRequest } from "./types";

type DraftFormState = {
  title: string;
  brief: string;
  objective: string;
  scope: string;
  outputFormat: string;
  constraints: string[];
  questions: string[];
  planSummary: string;
  planSteps: ResearchPlanStep[];
};

type ResearchDraftEditorProps = {
  draft: ResearchDraftRecord;
  busy: boolean;
  onSave: (request: UpdateResearchDraftRequest) => Promise<void>;
  onStart: () => Promise<void>;
  onDiscard: () => Promise<void>;
};

function normalizeDraft(draft: ResearchDraftRecord): DraftFormState {
  return {
    title: draft.title || "",
    brief: draft.brief || "",
    objective: draft.objective || "",
    scope: draft.scope || "",
    outputFormat: draft.outputFormat || "",
    constraints: draft.constraints || [],
    questions: draft.questions || [],
    planSummary: draft.planSummary || "",
    planSteps: draft.planSteps.map((step, index) => ({
      stepId: step.stepId || `step-${index + 1}`,
      title: step.title || "",
      objective: step.objective || "",
      query: step.query || "",
      useWeb: step.useWeb ?? true,
      useDocuments: step.useDocuments ?? false,
      outputFocus: step.outputFocus || ""
    }))
  };
}

export default function ResearchDraftEditor({
  draft,
  busy,
  onSave,
  onStart,
  onDiscard
}: ResearchDraftEditorProps) {
  const [form, setForm] = useState<DraftFormState>(() => normalizeDraft(draft));

  useEffect(() => {
    setForm(normalizeDraft(draft));
  }, [draft]);

  async function handleSave() {
    await onSave({
      revision: draft.revision,
      title: form.title,
      brief: form.brief,
      objective: form.objective,
      scope: form.scope,
      outputFormat: form.outputFormat,
      constraints: form.constraints.filter(Boolean),
      questions: form.questions.filter(Boolean),
      planSummary: form.planSummary,
      planSteps: form.planSteps
    });
  }

  function updateStep(stepId: string, patch: Partial<ResearchPlanStep>) {
    setForm((current) => ({
      ...current,
      planSteps: current.planSteps.map((step) => (step.stepId === stepId ? { ...step, ...patch } : step))
    }));
  }

  function addStep() {
    setForm((current) => ({
      ...current,
      planSteps: [
        ...current.planSteps,
        {
          stepId: `step-${current.planSteps.length + 1}`,
          title: "",
          objective: "",
          query: "",
          useWeb: true,
          useDocuments: false,
          outputFocus: ""
        }
      ]
    }));
  }

  function removeStep(stepId: string) {
    setForm((current) => ({
      ...current,
      planSteps: current.planSteps.filter((step) => step.stepId !== stepId)
    }));
  }

  function updateListField(field: "constraints" | "questions", index: number, value: string) {
    setForm((current) => {
      const newList = [...current[field]];
      newList[index] = value;
      return { ...current, [field]: newList };
    });
  }

  function addListFieldItem(field: "constraints" | "questions") {
    setForm((current) => ({
      ...current,
      [field]: [...current[field], ""]
    }));
  }

  function removeListFieldItem(field: "constraints" | "questions", index: number) {
    setForm((current) => {
      const newList = [...current[field]];
      newList.splice(index, 1);
      return { ...current, [field]: newList };
    });
  }

  return (
    <section className="inline-banner draft-editor-banner">
      <div className="stack-list draft-editor-content">
        <div>
          <span className={`mini-badge ${draft.ready ? "ready" : "pending"}`}>{draft.ready ? "准备就绪" : "计划草拟中"}</span>
          <strong>{draft.title}</strong>
          <p>{draft.planSummary || "深度研究草案已准备好审核。"}</p>
        </div>

        <label className="field">
          <span>标题</span>
          <input value={form.title} disabled={busy} onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))} />
        </label>

        <label className="field">
          <span>研究简述</span>
          <textarea value={form.brief} disabled={busy} onChange={(event) => setForm((current) => ({ ...current, brief: event.target.value }))} rows={4} />
        </label>

        <div className="resource-row">
          <label className="field" style={{ flex: 1 }}>
            <span>目标</span>
            <textarea value={form.objective} disabled={busy} onChange={(event) => setForm((current) => ({ ...current, objective: event.target.value }))} rows={3} />
          </label>
          <label className="field" style={{ flex: 1 }}>
            <span>范围</span>
            <textarea value={form.scope} disabled={busy} onChange={(event) => setForm((current) => ({ ...current, scope: event.target.value }))} rows={3} />
          </label>
        </div>

        <div className="resource-row">
          <label className="field" style={{ flex: 1 }}>
            <span>输出格式</span>
            <input value={form.outputFormat} disabled={busy} onChange={(event) => setForm((current) => ({ ...current, outputFormat: event.target.value }))} />
          </label>
          <label className="field" style={{ flex: 1 }}>
            <span>计划摘要</span>
            <input value={form.planSummary} disabled={busy} onChange={(event) => setForm((current) => ({ ...current, planSummary: event.target.value }))} />
          </label>
        </div>

        <div className="resource-row">
          <div className="field" style={{ flex: 1 }}>
            <div className="section-title-row" style={{ marginBottom: "8px" }}>
              <span>约束条件</span>
              <button className="ghost-button" disabled={busy} onClick={() => addListFieldItem("constraints")}>新增约束</button>
            </div>
            {form.constraints.map((constraint, index) => (
              <div key={index} style={{ display: "flex", gap: "8px", marginBottom: "8px" }}>
                <input value={constraint} disabled={busy} onChange={(e) => updateListField("constraints", index, e.target.value)} style={{ flex: 1 }} />
                <button className="ghost-button" disabled={busy} onClick={() => removeListFieldItem("constraints", index)}>删除</button>
              </div>
            ))}
          </div>
          <div className="field" style={{ flex: 1 }}>
            <div className="section-title-row" style={{ marginBottom: "8px" }}>
              <span>待确认问题</span>
              <button className="ghost-button" disabled={busy} onClick={() => addListFieldItem("questions")}>新增问题</button>
            </div>
            {form.questions.map((question, index) => (
              <div key={index} style={{ display: "flex", gap: "8px", marginBottom: "8px" }}>
                <input value={question} disabled={busy} onChange={(e) => updateListField("questions", index, e.target.value)} style={{ flex: 1 }} />
                <button className="ghost-button" disabled={busy} onClick={() => removeListFieldItem("questions", index)}>删除</button>
              </div>
            ))}
          </div>
        </div>

        <div className="stack-list">
          <div className="section-title-row">
            <strong>计划步骤</strong>
            <button className="ghost-button" disabled={busy} onClick={addStep}>新增步骤</button>
          </div>
          {form.planSteps.length === 0 ? <div className="empty-card">暂无研究步骤。</div> : null}
          {form.planSteps.map((step) => (
            <div key={step.stepId} className="stack-list message-bubble">
              <div className="resource-row">
                <label className="field" style={{ flex: 1 }}>
                  <span>标题</span>
                  <input value={step.title} disabled={busy} onChange={(event) => updateStep(step.stepId, { title: event.target.value })} />
                </label>
                <label className="field" style={{ flex: 1 }}>
                  <span>输出重点</span>
                  <input value={step.outputFocus} disabled={busy} onChange={(event) => updateStep(step.stepId, { outputFocus: event.target.value })} />
                </label>
              </div>
              <label className="field">
                <span>目标</span>
                <textarea value={step.objective} disabled={busy} onChange={(event) => updateStep(step.stepId, { objective: event.target.value })} rows={2} />
              </label>
              <label className="field">
                <span>检索意图 / Query</span>
                <textarea value={step.query} disabled={busy} onChange={(event) => updateStep(step.stepId, { query: event.target.value })} rows={2} />
              </label>
              <div className="inline-actions">
                <label className="field-checkbox">
                  <input type="checkbox" checked={step.useWeb} disabled={busy} onChange={(event) => updateStep(step.stepId, { useWeb: event.target.checked })} />
                  <span>使用 Web</span>
                </label>
                <label className="field-checkbox">
                  <input type="checkbox" checked={step.useDocuments} disabled={busy} onChange={(event) => updateStep(step.stepId, { useDocuments: event.target.checked })} />
                  <span>使用文档</span>
                </label>
                <button className="ghost-button" disabled={busy} onClick={() => removeStep(step.stepId)}>删除</button>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="inline-actions">
        <button className="ghost-button" disabled={busy} onClick={() => void handleSave()}>保存计划</button>
        <button className="primary-button" disabled={!draft.ready || busy} onClick={() => void onStart()}>开始研究</button>
        <button className="ghost-button" disabled={busy} onClick={() => void onDiscard()}>放弃计划</button>
      </div>
    </section>
  );
}

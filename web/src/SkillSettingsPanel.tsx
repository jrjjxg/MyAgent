import { useEffect, useMemo, useState } from "react";
import type { SkillStatusRecord, SkillStatusResponse, UpdateSkillConfigRequest } from "./types";

type SkillDraft = {
  enabled: boolean;
  apiKey: string;
  apiKeyTouched: boolean;
  env: Record<string, string>;
  touchedEnvKeys: Record<string, boolean>;
};

type Props = {
  open: boolean;
  loading: boolean;
  response: SkillStatusResponse | null;
  onClose: () => void;
  onRefresh: () => void;
  onSave: (skillId: string, request: UpdateSkillConfigRequest) => Promise<void>;
};

function createDraft(skill: SkillStatusRecord): SkillDraft {
  const envEntries = Object.fromEntries(skill.requiredEnvs.filter((key) => key !== skill.primaryEnv).map((key) => [key, ""]));
  return {
    enabled: skill.enabled,
    apiKey: "",
    apiKeyTouched: false,
    env: envEntries,
    touchedEnvKeys: {}
  };
}

function isConfigured(skill: SkillStatusRecord, key: string) {
  return skill.configuredEnvKeys.includes(key);
}

function sectionSkills(skills: SkillStatusRecord[], mode: "ready" | "needsSetup" | "disabled") {
  const filtered = skills.filter((skill) => {
    if (mode === "ready") return skill.enabled && skill.ready;
    if (mode === "needsSetup") return skill.enabled && !skill.ready;
    return !skill.enabled;
  });
  return [...filtered].sort((left, right) => left.skillId.localeCompare(right.skillId));
}

export default function SkillSettingsPanel({ open, loading, response, onClose, onRefresh, onSave }: Props) {
  const [drafts, setDrafts] = useState<Record<string, SkillDraft>>({});
  const [savingSkillId, setSavingSkillId] = useState<string | null>(null);
  const [panelError, setPanelError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    onRefresh();
  }, [open]);

  useEffect(() => {
    if (!response) return;
    setDrafts(Object.fromEntries(response.skills.map((skill) => [skill.skillId, createDraft(skill)])));
  }, [response]);

  const readySkills = useMemo(() => sectionSkills(response?.skills || [], "ready"), [response]);
  const setupSkills = useMemo(() => sectionSkills(response?.skills || [], "needsSetup"), [response]);
  const disabledSkills = useMemo(() => sectionSkills(response?.skills || [], "disabled"), [response]);

  if (!open) return null;

  async function handleSave(skill: SkillStatusRecord) {
    const draft = drafts[skill.skillId] || createDraft(skill);
    const envPayload = Object.fromEntries(
      Object.entries(draft.env).filter(([key]) => draft.touchedEnvKeys[key])
    );
    const request: UpdateSkillConfigRequest = {
      enabled: draft.enabled
    };
    if (draft.apiKeyTouched) {
      request.apiKey = draft.apiKey;
    }
    if (Object.keys(envPayload).length > 0) {
      request.env = envPayload;
    }
    setSavingSkillId(skill.skillId);
    setPanelError(null);
    try {
      await onSave(skill.skillId, request);
    } catch (error) {
      setPanelError((error as Error).message);
    } finally {
      setSavingSkillId(null);
    }
  }

  function updateDraft(skillId: string, updater: (current: SkillDraft) => SkillDraft) {
    setDrafts((current) => {
      const skill = response?.skills.find((item) => item.skillId === skillId);
      if (!skill) {
        return current;
      }
      const base = current[skillId] || createDraft(skill);
      return { ...current, [skillId]: updater(base) };
    });
  }

  function renderSkillCard(skill: SkillStatusRecord) {
    const draft = drafts[skill.skillId] || createDraft(skill);
    const extraEnvKeys = skill.requiredEnvs.filter((key) => key !== skill.primaryEnv);
    const saving = savingSkillId === skill.skillId;
    return (
      <article key={skill.skillId} className="skill-card">
        <div className="skill-card-header">
          <div>
            <div className="skill-card-title-row">
              <strong>{skill.skillId}</strong>
              <span className={`skill-state ${skill.enabled ? (skill.ready ? "ready" : "pending") : "disabled"}`}>
                {skill.enabled ? (skill.ready ? "已就绪" : "需要设置") : "已禁用"}
              </span>
            </div>
            <p>{skill.summary || skill.description}</p>
          </div>
          <label className="skill-enable-toggle">
            <input
              type="checkbox"
              checked={draft.enabled}
              onChange={(event) => updateDraft(skill.skillId, (current) => ({ ...current, enabled: event.target.checked }))}
            />
            <span>启用</span>
          </label>
        </div>

        <div className="skill-meta-grid">
          <span>来源: {skill.source}</span>
          {skill.homepage ? (
            <a href={skill.homepage} target="_blank" rel="noreferrer">
              主页
            </a>
          ) : (
            <span>路径: {skill.path}</span>
          )}
          {skill.requiresWeb ? <span>需要网络访问</span> : null}
          {skill.requiresDocuments ? <span>支持文档处理</span> : null}
        </div>

        <div className="skill-chip-row">
          {(skill.primaryEnv ? [skill.primaryEnv, ...extraEnvKeys] : extraEnvKeys).map((key) => (
            <span key={key} className={`skill-chip ${isConfigured(skill, key) ? "configured" : skill.missingEnvs.includes(key) ? "missing" : ""}`}>
              {key}
            </span>
          ))}
        </div>

        {skill.primaryEnv ? (
          <label className="field">
            <span>API 密钥</span>
            <div className="skill-secret-row">
              <input
                type="password"
                value={draft.apiKey}
                placeholder={isConfigured(skill, skill.primaryEnv) ? `已为 ${skill.primaryEnv} 配置` : skill.primaryEnv}
                onChange={(event) =>
                  updateDraft(skill.skillId, (current) => ({
                    ...current,
                    apiKey: event.target.value,
                    apiKeyTouched: true
                  }))
                }
              />
              {isConfigured(skill, skill.primaryEnv) ? (
                <button
                  type="button"
                  className="text-button"
                  onClick={() =>
                    updateDraft(skill.skillId, (current) => ({
                      ...current,
                      apiKey: "",
                      apiKeyTouched: true
                    }))
                  }
                >
                  清除
                </button>
              ) : null}
            </div>
          </label>
        ) : null}

        {extraEnvKeys.map((key) => (
          <label key={key} className="field">
            <span>{key}</span>
            <div className="skill-secret-row">
              <input
                type="password"
                value={draft.env[key] || ""}
                placeholder={isConfigured(skill, key) ? "已配置" : key}
                onChange={(event) =>
                  updateDraft(skill.skillId, (current) => ({
                    ...current,
                    env: { ...current.env, [key]: event.target.value },
                    touchedEnvKeys: { ...current.touchedEnvKeys, [key]: true }
                  }))
                }
              />
              {isConfigured(skill, key) ? (
                <button
                  type="button"
                  className="text-button"
                  onClick={() =>
                    updateDraft(skill.skillId, (current) => ({
                      ...current,
                      env: { ...current.env, [key]: "" },
                      touchedEnvKeys: { ...current.touchedEnvKeys, [key]: true }
                    }))
                  }
                >
                  清除
                </button>
              ) : null}
            </div>
          </label>
        ))}

        <div className="skill-card-actions">
          <button
            className="primary-button"
            disabled={!response?.secretStorageAvailable || saving}
            onClick={() => void handleSave(skill)}
          >
            {saving ? "保存中..." : "保存"}
          </button>
          <span className="skill-hint">
            {skill.missingEnvs.length === 0 ? "所有声明的环境变量要求均已满足。" : `缺失: ${skill.missingEnvs.join(", ")}`}
          </span>
        </div>
      </article>
    );
  }

  return (
    <div className="skills-panel-overlay" role="dialog" aria-modal="true">
      <section className="skills-panel">
        <header className="skills-panel-header">
          <div>
            <span className="conversation-kicker">技能</span>
            <h2>技能设置</h2>
            <p>启用技能并仅配置它们声明的环境变量键。</p>
          </div>
          <div className="skills-panel-actions">
            <button className="ghost-button" onClick={onRefresh} disabled={loading}>
              {loading ? "刷新中..." : "刷新"}
            </button>
            <button className="ghost-button" onClick={onClose}>关闭</button>
          </div>
        </header>

        {response && !response.secretStorageAvailable ? (
          <div className="page-error">服务器尚未启用技能密钥存储。您可以查看要求，但无法保存。</div>
        ) : null}
        {panelError ? <div className="page-error">{panelError}</div> : null}

        <div className="skill-section">
          <div className="section-title-row">
            <strong>已就绪</strong>
            <span>{readySkills.length}</span>
          </div>
          {readySkills.length === 0 ? <div className="empty-card">暂无就绪的技能。</div> : readySkills.map(renderSkillCard)}
        </div>

        <div className="skill-section">
          <div className="section-title-row">
            <strong>需要设置</strong>
            <span>{setupSkills.length}</span>
          </div>
          {setupSkills.length === 0 ? <div className="empty-card">目前没有需要设置的技能。</div> : setupSkills.map(renderSkillCard)}
        </div>

        <div className="skill-section">
          <div className="section-title-row">
            <strong>已禁用</strong>
            <span>{disabledSkills.length}</span>
          </div>
          {disabledSkills.length === 0 ? <div className="empty-card">没有已禁用的技能。</div> : disabledSkills.map(renderSkillCard)}
        </div>
      </section>
    </div>
  );
}

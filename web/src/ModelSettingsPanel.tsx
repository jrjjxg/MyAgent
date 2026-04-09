import { useEffect, useMemo, useState } from "react";
import type { ModelProviderStatusRecord, ModelProviderStatusResponse, UpdateModelProviderConfigRequest } from "./types";

type ProviderDraft = {
  enabled: boolean;
  apiKey: string;
  apiKeyTouched: boolean;
  model: string;
  modelTouched: boolean;
  baseUrl: string;
  baseUrlTouched: boolean;
};

type Props = {
  open: boolean;
  loading: boolean;
  response: ModelProviderStatusResponse | null;
  onClose: () => void;
  onRefresh: () => void;
  onSave: (providerId: string, request: UpdateModelProviderConfigRequest) => Promise<void>;
};

function createDraft(provider: ModelProviderStatusRecord): ProviderDraft {
  return {
    enabled: provider.enabled,
    apiKey: "",
    apiKeyTouched: false,
    model: provider.customModel || "",
    modelTouched: false,
    baseUrl: provider.customBaseUrl || "",
    baseUrlTouched: false
  };
}

function sectionProviders(providers: ModelProviderStatusRecord[], mode: "ready" | "needsSetup" | "disabled") {
  const filtered = providers.filter((provider) => {
    if (mode === "ready") return provider.enabled && provider.ready;
    if (mode === "needsSetup") return provider.enabled && !provider.ready;
    return !provider.enabled;
  });
  return [...filtered].sort((left, right) => left.providerId.localeCompare(right.providerId));
}

export default function ModelSettingsPanel({ open, loading, response, onClose, onRefresh, onSave }: Props) {
  const [drafts, setDrafts] = useState<Record<string, ProviderDraft>>({});
  const [savingProviderId, setSavingProviderId] = useState<string | null>(null);
  const [panelError, setPanelError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    onRefresh();
  }, [open]);

  useEffect(() => {
    if (!response) return;
    setDrafts(Object.fromEntries(response.providers.map((provider) => [provider.providerId, createDraft(provider)])));
  }, [response]);

  const readyProviders = useMemo(() => sectionProviders(response?.providers || [], "ready"), [response]);
  const setupProviders = useMemo(() => sectionProviders(response?.providers || [], "needsSetup"), [response]);
  const disabledProviders = useMemo(() => sectionProviders(response?.providers || [], "disabled"), [response]);

  if (!open) return null;

  function updateDraft(providerId: string, updater: (current: ProviderDraft) => ProviderDraft) {
    setDrafts((current) => {
      const provider = response?.providers.find((item) => item.providerId === providerId);
      if (!provider) return current;
      const base = current[providerId] || createDraft(provider);
      return { ...current, [providerId]: updater(base) };
    });
  }

  async function handleSave(provider: ModelProviderStatusRecord) {
    const draft = drafts[provider.providerId] || createDraft(provider);
    const request: UpdateModelProviderConfigRequest = {
      enabled: draft.enabled
    };
    if (draft.apiKeyTouched) {
      request.apiKey = draft.apiKey;
    }
    if (draft.modelTouched) {
      request.model = draft.model;
    }
    if (provider.supportsCustomBaseUrl && draft.baseUrlTouched) {
      request.baseUrl = draft.baseUrl;
    }
    setSavingProviderId(provider.providerId);
    setPanelError(null);
    try {
      await onSave(provider.providerId, request);
    } catch (error) {
      setPanelError((error as Error).message);
    } finally {
      setSavingProviderId(null);
    }
  }

  function renderProviderCard(provider: ModelProviderStatusRecord) {
    const draft = drafts[provider.providerId] || createDraft(provider);
    const saving = savingProviderId === provider.providerId;
    return (
      <article key={provider.providerId} className="skill-card">
        <div className="skill-card-header">
          <div>
            <div className="skill-card-title-row">
              <strong>{provider.displayName}</strong>
              <span className={`skill-state ${provider.enabled ? (provider.ready ? "ready" : "pending") : "disabled"}`}>
                {provider.enabled ? (provider.ready ? "已就绪" : "需要设置") : "已禁用"}
              </span>
            </div>
            <p>为当前用户配置此提供商，无需重启后端。</p>
          </div>
          <label className="skill-enable-toggle">
            <input
              type="checkbox"
              checked={draft.enabled}
              onChange={(event) => updateDraft(provider.providerId, (current) => ({ ...current, enabled: event.target.checked }))}
            />
            <span>启用</span>
          </label>
        </div>

        <div className="skill-meta-grid">
          <span>提供商 ID: {provider.providerId}</span>
          <span>有效模型: {provider.effectiveModel || "--"}</span>
          <span>Base URL: {provider.effectiveBaseUrl || "默认"}</span>
        </div>

        <div className="skill-chip-row">
          <span className={`skill-chip ${provider.apiKeyConfigured ? "configured" : "missing"}`}>API 密钥</span>
          <span className={`skill-chip ${provider.effectiveModel ? "configured" : "missing"}`}>模型</span>
          {provider.supportsCustomBaseUrl ? (
            <span className={`skill-chip ${provider.effectiveBaseUrl ? "configured" : ""}`}>Base URL</span>
          ) : null}
        </div>

        <label className="field">
          <span>API 密钥</span>
          <div className="skill-secret-row">
            <input
              type="password"
              value={draft.apiKey}
              placeholder={
                provider.customApiKeyConfigured
                  ? "已为此用户配置"
                  : provider.systemApiKeyConfigured
                  ? "使用服务器级 API 密钥"
                  : "粘贴 API 密钥"
              }
              onChange={(event) =>
                updateDraft(provider.providerId, (current) => ({
                  ...current,
                  apiKey: event.target.value,
                  apiKeyTouched: true
                }))
              }
            />
            {provider.apiKeyConfigured ? (
              <button
                type="button"
                className="text-button"
                onClick={() =>
                  updateDraft(provider.providerId, (current) => ({
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

        <label className="field">
          <span>模型</span>
          <input
            type="text"
            value={draft.model}
            placeholder={provider.effectiveModel || "提供商默认模型"}
            onChange={(event) =>
              updateDraft(provider.providerId, (current) => ({
                ...current,
                model: event.target.value,
                modelTouched: true
              }))
            }
          />
        </label>

        {provider.supportsCustomBaseUrl ? (
          <label className="field">
            <span>Base URL</span>
            <input
              type="text"
              value={draft.baseUrl}
              placeholder={provider.effectiveBaseUrl || "提供商默认 Base URL"}
              onChange={(event) =>
                updateDraft(provider.providerId, (current) => ({
                  ...current,
                  baseUrl: event.target.value,
                  baseUrlTouched: true
                }))
              }
            />
          </label>
        ) : null}

        <div className="skill-card-actions">
          <button className="primary-button" disabled={saving} onClick={() => void handleSave(provider)}>
            {saving ? "保存中..." : "保存"}
          </button>
          <span className="skill-hint">
            {provider.ready ? "此提供商已准备就绪。" : "添加 API 密钥和模型以使此提供商可用。"}
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
            <span className="conversation-kicker">模型</span>
            <h2>模型设置</h2>
            <p>按用户保存提供商凭据，无需重启后端即可使用。</p>
          </div>
          <div className="skills-panel-actions">
            <button className="ghost-button" onClick={onRefresh} disabled={loading}>
              {loading ? "刷新中..." : "刷新"}
            </button>
            <button className="ghost-button" onClick={onClose}>关闭</button>
          </div>
        </header>

        {response && !response.secretStorageAvailable ? (
          <div className="page-error">未启用服务器端密钥加密。您仍然可以编辑非机密字段，但保存 API 密钥将失败。</div>
        ) : null}
        {panelError ? <div className="page-error">{panelError}</div> : null}

        <div className="skill-section">
          <div className="section-title-row">
            <strong>已就绪</strong>
            <span>{readyProviders.length}</span>
          </div>
          {readyProviders.length === 0 ? <div className="empty-card">暂无就绪的提供商。</div> : readyProviders.map(renderProviderCard)}
        </div>

        <div className="skill-section">
          <div className="section-title-row">
            <strong>需要设置</strong>
            <span>{setupProviders.length}</span>
          </div>
          {setupProviders.length === 0 ? <div className="empty-card">目前没有需要设置的提供商。</div> : setupProviders.map(renderProviderCard)}
        </div>

        <div className="skill-section">
          <div className="section-title-row">
            <strong>已禁用</strong>
            <span>{disabledProviders.length}</span>
          </div>
          {disabledProviders.length === 0 ? <div className="empty-card">没有已禁用的提供商。</div> : disabledProviders.map(renderProviderCard)}
        </div>
      </section>
    </div>
  );
}

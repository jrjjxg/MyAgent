import { useEffect, useState } from "react";
import type { UpdateWebSearchSettingsRequest, WebSearchSettingsResponse } from "./types";

type Draft = {
  provider: string;
  providerTouched: boolean;
  tavilyApiKey: string;
  tavilyApiKeyTouched: boolean;
  searchApiBaseUrl: string;
  searchApiBaseUrlTouched: boolean;
};

type Props = {
  open: boolean;
  loading: boolean;
  response: WebSearchSettingsResponse | null;
  onClose: () => void;
  onRefresh: () => void;
  onSave: (request: UpdateWebSearchSettingsRequest) => Promise<void>;
};

function createDraft(response: WebSearchSettingsResponse | null): Draft {
  return {
    provider: response?.settings.customProvider || response?.settings.effectiveProvider || "auto",
    providerTouched: false,
    tavilyApiKey: "",
    tavilyApiKeyTouched: false,
    searchApiBaseUrl: response?.settings.customSearchApiBaseUrl || "",
    searchApiBaseUrlTouched: false
  };
}

export default function WebSearchSettingsPanel({ open, loading, response, onClose, onRefresh, onSave }: Props) {
  const [draft, setDraft] = useState<Draft>(() => createDraft(response));
  const [panelError, setPanelError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    onRefresh();
  }, [open]);

  useEffect(() => {
    setDraft(createDraft(response));
  }, [response]);

  if (!open) return null;

  async function handleSave() {
    const request: UpdateWebSearchSettingsRequest = {};
    if (draft.providerTouched) {
      request.provider = draft.provider;
    }
    if (draft.tavilyApiKeyTouched) {
      request.tavilyApiKey = draft.tavilyApiKey;
    }
    if (draft.searchApiBaseUrlTouched) {
      request.searchApiBaseUrl = draft.searchApiBaseUrl;
    }
    setSaving(true);
    setPanelError(null);
    try {
      await onSave(request);
    } catch (error) {
      setPanelError((error as Error).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="skills-panel-overlay" role="dialog" aria-modal="true">
      <section className="skills-panel">
        <header className="skills-panel-header">
          <div>
            <span className="conversation-kicker">搜索</span>
            <h2>网络搜索设置</h2>
            <p>为当前用户配置搜索提供商和 Tavily API 密钥。</p>
          </div>
          <div className="skills-panel-actions">
            <button className="ghost-button" onClick={onRefresh} disabled={loading}>
              {loading ? "刷新中..." : "刷新"}
            </button>
            <button className="ghost-button" onClick={onClose}>关闭</button>
          </div>
        </header>

        {response && !response.secretStorageAvailable ? (
          <div className="page-error">未启用服务器端密钥加密。您仍然可以更改提供商/Base URL，但保存 Tavily API 密钥将失败。</div>
        ) : null}
        {panelError ? <div className="page-error">{panelError}</div> : null}

        <div className="skill-section">
          <article className="skill-card">
            <div className="skill-card-header">
              <div>
                <div className="skill-card-title-row">
                  <strong>网络搜索运行时</strong>
                  <span className={`skill-state ${response?.settings.tavilyApiKeyConfigured ? "ready" : "pending"}`}>
                    {response?.settings.effectiveProvider || "auto"}
                  </span>
                </div>
                <p>当提供商设置为 Auto 时，如果当前用户或服务器配置了 Tavily API 密钥，则会使用 Tavily。</p>
              </div>
            </div>

            <div className="skill-meta-grid">
              <span>有效提供商: {response?.settings.effectiveProvider || "auto"}</span>
              <span>搜索 Base URL: {response?.settings.effectiveSearchApiBaseUrl || "--"}</span>
            </div>

            <div className="skill-chip-row">
              <span className={`skill-chip ${response?.settings.tavilyApiKeyConfigured ? "configured" : "missing"}`}>Tavily 密钥</span>
              <span className="skill-chip configured">提供商</span>
              <span className="skill-chip configured">Base URL</span>
            </div>

            <label className="field">
              <span>提供商</span>
              <select
                value={draft.provider}
                onChange={(event) =>
                  setDraft((current) => ({
                    ...current,
                    provider: event.target.value,
                    providerTouched: true
                  }))
                }
              >
                <option value="auto">自动 (Auto)</option>
                <option value="tavily">Tavily</option>
                <option value="duckduckgo">DuckDuckGo</option>
              </select>
            </label>

            <label className="field">
              <span>Tavily API 密钥</span>
              <div className="skill-secret-row">
                <input
                  type="password"
                  value={draft.tavilyApiKey}
                  placeholder={
                    response?.settings.customTavilyApiKeyConfigured
                      ? "已为此用户配置"
                      : response?.settings.systemTavilyApiKeyConfigured
                      ? "使用服务器级 Tavily 密钥"
                      : "粘贴 Tavily API 密钥"
                  }
                  onChange={(event) =>
                    setDraft((current) => ({
                      ...current,
                      tavilyApiKey: event.target.value,
                      tavilyApiKeyTouched: true
                    }))
                  }
                />
                {response?.settings.tavilyApiKeyConfigured ? (
                  <button
                    type="button"
                    className="text-button"
                    onClick={() =>
                      setDraft((current) => ({
                        ...current,
                        tavilyApiKey: "",
                        tavilyApiKeyTouched: true
                      }))
                    }
                  >
                    清除
                  </button>
                ) : null}
              </div>
            </label>

            <label className="field">
              <span>搜索 Base URL</span>
              <input
                type="text"
                value={draft.searchApiBaseUrl}
                placeholder={response?.settings.effectiveSearchApiBaseUrl || "默认提供商 URL"}
                onChange={(event) =>
                  setDraft((current) => ({
                    ...current,
                    searchApiBaseUrl: event.target.value,
                    searchApiBaseUrlTouched: true
                  }))
                }
              />
            </label>

            <div className="skill-card-actions">
              <button className="primary-button" disabled={saving} onClick={() => void handleSave()}>
                {saving ? "保存中..." : "保存"}
              </button>
              <span className="skill-hint">
                {response?.settings.tavilyApiKeyConfigured
                  ? "此用户可使用 Tavily。"
                  : "添加 Tavily API 密钥以使自动搜索升级到 DuckDuckGo 之外的服务。"}
              </span>
            </div>
          </article>
        </div>
      </section>
    </div>
  );
}

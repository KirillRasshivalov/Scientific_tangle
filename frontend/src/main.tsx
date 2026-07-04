import React, { FormEvent, useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  ArrowRight,
  Database,
  FileSearch,
  Filter,
  FlaskConical,
  GitBranch,
  Lock,
  LogOut,
  ShieldCheck,
  UserPlus
} from "lucide-react";
import {
  authRequest,
  AuthResponse,
  KnowledgeUploadResponse,
  ping,
  ResearchQueryResponse,
  sendResearchQuery,
  uploadKnowledgeFile
} from "./api";
import "./styles.css";

type AuthMode = "login" | "register";

const STORAGE_KEY = "scientific-tangle-auth";

function loadSession(): AuthResponse | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;

  try {
    return JSON.parse(raw) as AuthResponse;
  } catch {
    localStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

function App() {
  const [session, setSession] = useState<AuthResponse | null>(loadSession);
  const [mode, setMode] = useState<AuthMode>("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [serverState, setServerState] = useState("Проверяем сервер...");
  const [query, setQuery] = useState("");
  const [queryError, setQueryError] = useState("");
  const [isQueryLoading, setIsQueryLoading] = useState(false);
  const [queryResult, setQueryResult] = useState<ResearchQueryResponse | null>(null);
  const [uploadError, setUploadError] = useState("");
  const [isUploadLoading, setIsUploadLoading] = useState(false);
  const [uploadResult, setUploadResult] = useState<KnowledgeUploadResponse | null>(null);

  useEffect(() => {
    ping(session?.token)
      .then(() => setServerState("Java API доступен"))
      .catch(() => setServerState("Java API недоступен"));
  }, [session?.token]);

  const canSubmit = useMemo(
    () => username.trim().length >= 3 && password.length >= 4 && !isSubmitting,
    [isSubmitting, password.length, username]
  );

  const canSendQuery = query.trim().length >= 8 && !isQueryLoading;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setIsSubmitting(true);

    try {
      const nextSession = await authRequest(mode, {
        username: username.trim(),
        password
      });
      localStorage.setItem(STORAGE_KEY, JSON.stringify(nextSession));
      setSession(nextSession);
      setPassword("");
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "Не удалось выполнить запрос."
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleResearchQuery(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !canSendQuery) return;

    setQueryError("");
    setIsQueryLoading(true);

    try {
      const result = await sendResearchQuery(session.token, {
        query: query.trim(),
        filters: {
          material: "Любой",
          process: "Любой",
          geography: "Россия и зарубежная практика",
          period: "Без ограничения",
          confidence: "Любой уровень"
        }
      });
      setQueryResult(result);
    } catch (requestError) {
      setQueryError(
        requestError instanceof Error
          ? requestError.message
          : "Не удалось отправить запрос в Java API."
      );
    } finally {
      setIsQueryLoading(false);
    }
  }

  async function handleKnowledgeUpload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) return;

    const form = event.currentTarget;
    const fileInput = form.elements.namedItem("knowledgeFile") as HTMLInputElement | null;
    const file = fileInput?.files?.[0];

    if (!file) {
      setUploadError("Выберите PDF или DOCX файл.");
      return;
    }

    setUploadError("");
    setIsUploadLoading(true);

    try {
      const result = await uploadKnowledgeFile(session.token, file);
      setUploadResult(result);
      form.reset();
    } catch (requestError) {
      setUploadError(
        requestError instanceof Error
          ? requestError.message
          : "Не удалось загрузить файл в базу знаний."
      );
    } finally {
      setIsUploadLoading(false);
    }
  }

  function logout() {
    localStorage.removeItem(STORAGE_KEY);
    setSession(null);
    setQueryResult(null);
    setUploadResult(null);
  }

  if (session) {
    return (
      <main className="app-shell">
        <section className="workspace">
          <header className="topbar">
            <div>
              <span className="eyebrow">R&D knowledge map</span>
              <h1>Карта знаний горно-металлургических исследований</h1>
            </div>
            <div className="user-panel">
              <div className="identity">
                <strong>{session.username}</strong>
                <span>{session.role}</span>
              </div>
              <button className="icon-button" type="button" onClick={logout} title="Выйти">
                <LogOut size={20} aria-hidden="true" />
              </button>
            </div>
          </header>

          <section className="query-panel">
            <form className="query-form" onSubmit={handleResearchQuery}>
              <div className="query-input">
                <FileSearch size={22} aria-hidden="true" />
                <input
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="Например: методы обессоливания воды при сульфатах и хлоридах 200-300 мг/л"
                  type="search"
                  value={query}
                />
                <button disabled={!canSendQuery} type="submit">
                  <ArrowRight size={18} aria-hidden="true" />
                  {isQueryLoading ? "Ищем..." : "Найти"}
                </button>
              </div>
              {queryError ? <p className="error-message">{queryError}</p> : null}
            </form>
            <div className="filters">
              <button type="button">
                <Filter size={17} aria-hidden="true" />
                Материал
              </button>
              <button type="button">Процесс</button>
              <button type="button">География</button>
              <button type="button">Период</button>
              <button type="button">Достоверность</button>
            </div>
          </section>

          <section className="dashboard-grid">
            <article className="metric-card">
              <Database size={22} aria-hidden="true" />
              <span>Источники</span>
              <strong>1M+</strong>
            </article>
            <article className="metric-card">
              <GitBranch size={22} aria-hidden="true" />
              <span>Связи графа</span>
              <strong>3-4 уровня</strong>
            </article>
            <article className="metric-card">
              <FlaskConical size={22} aria-hidden="true" />
              <span>Эксперименты</span>
              <strong>Параметры и режимы</strong>
            </article>
          </section>

          {session.role === "ANALYST" ? (
            <section className="upload-panel">
              <div>
                <span className="eyebrow">Knowledge base</span>
                <h2>Загрузить в базу знаний</h2>
              </div>
              <form className="upload-form" onSubmit={handleKnowledgeUpload}>
                <input
                  accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                  name="knowledgeFile"
                  type="file"
                />
                <button disabled={isUploadLoading} type="submit">
                  {isUploadLoading ? "Загружаем..." : "Загрузить"}
                </button>
              </form>
              {uploadError ? <p className="error-message">{uploadError}</p> : null}
              {uploadResult ? (
                <p className="upload-status">
                  Файл `{uploadResult.originalFilename}` сохранен в MinIO:
                  `{uploadResult.bucket}/{uploadResult.objectName}`
                </p>
              ) : null}
            </section>
          ) : null}

          <section className="results-layout">
            <article className="answer-panel">
              <div className="panel-title">
                <h2>{queryResult ? "Ответ Java API" : "Черновик ответа"}</h2>
                <span>{queryResult ? queryResult.status : serverState}</span>
              </div>
              {queryResult ? (
                <>
                  <p className="answer-text">{queryResult.answer}</p>
                  <div className="result-meta">
                    <span>ID: {queryResult.requestId}</span>
                    <span>Confidence: {Math.round(queryResult.confidence * 100)}%</span>
                    <span>Updated: {new Date(queryResult.updatedAt).toLocaleString("ru-RU")}</span>
                  </div>
                  <div className="source-list">
                    {queryResult.sources.map((source) => (
                      <div className="source-item" key={`${source.title}-${source.type}`}>
                        <strong>{source.title}</strong>
                        <span>{source.type}</span>
                      </div>
                    ))}
                  </div>
                </>
              ) : (
                <p>
                  Здесь появится структурированный ответ от Java API. Пока Python-сервисов
                  нет, Java вернет заглушку и запишет пользовательский запрос в файл логов.
                </p>
              )}
              <div className="source-row">
                <ShieldCheck size={18} aria-hidden="true" />
                <span>JWT передается в защищенный endpoint `/api/research/query`.</span>
              </div>
            </article>
            <article className="graph-panel">
              <h2>Цепочка знаний</h2>
              <div className="graph-line">
                {(queryResult?.graphPath ?? ["Material", "Process", "Equipment", "Result"]).map(
                  (node) => (
                    <span key={node}>{node}</span>
                  )
                )}
              </div>
            </article>
          </section>
        </section>
      </main>
    );
  }

  return (
    <main className="auth-screen">
      <section className="auth-card" aria-labelledby="auth-title">
        <div className="brand-mark">
          <GitBranch size={28} aria-hidden="true" />
        </div>
        <span className="eyebrow">{serverState}</span>
        <h1 id="auth-title">
          {mode === "login" ? "Вход в R&D карту" : "Регистрация исследователя"}
        </h1>
        <form onSubmit={handleSubmit}>
          <label>
            Логин
            <input
              autoComplete="username"
              minLength={3}
              onChange={(event) => setUsername(event.target.value)}
              placeholder="researcher"
              required
              type="text"
              value={username}
            />
          </label>
          <label>
            Пароль
            <input
              autoComplete={mode === "login" ? "current-password" : "new-password"}
              minLength={4}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="••••••••"
              required
              type="password"
              value={password}
            />
          </label>
          {error ? <p className="error-message">{error}</p> : null}
          <button className="primary-action" disabled={!canSubmit} type="submit">
            {mode === "login" ? <Lock size={18} aria-hidden="true" /> : <UserPlus size={18} aria-hidden="true" />}
            {isSubmitting
              ? "Отправляем..."
              : mode === "login"
                ? "Войти"
                : "Создать аккаунт"}
          </button>
        </form>
        <button
          className="mode-switch"
          type="button"
          onClick={() => {
            setError("");
            setMode(mode === "login" ? "register" : "login");
          }}
        >
          {mode === "login" ? "Создать новый аккаунт" : "Уже есть аккаунт"}
        </button>
      </section>
    </main>
  );
}

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

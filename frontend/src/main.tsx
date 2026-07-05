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
  getKnowledgeGraph,
  KnowledgeGraphResponse,
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
  const [graphData, setGraphData] = useState<KnowledgeGraphResponse | null>(null);
  const [graphError, setGraphError] = useState("");
  const [graphSearch, setGraphSearch] = useState("");
  const [graphType, setGraphType] = useState("");
  const [selectedGraphNodeId, setSelectedGraphNodeId] = useState<string | null>(null);
  const [linkDistance, setLinkDistance] = useState(230);

  useEffect(() => {
    ping(session?.token)
      .then(() => setServerState("Java API доступен"))
      .catch(() => setServerState("Java API недоступен"));
  }, [session?.token]);

  useEffect(() => {
    if (!session) {
      setGraphData(null);
      return;
    }

    const timeoutId = window.setTimeout(() => {
      getKnowledgeGraph(session.token, {
        search: graphSearch.trim(),
        type: graphType,
        depth: 2,
        limit: 80
      })
        .then((data) => {
          setGraphData(data);
          setSelectedGraphNodeId(data.nodes[0]?.id ?? null);
          setGraphError("");
        })
        .catch((requestError) => {
          setGraphError(
            requestError instanceof Error
              ? requestError.message
              : "Не удалось загрузить граф знаний."
          );
        });
    }, 350);

    return () => window.clearTimeout(timeoutId);
  }, [session?.token, graphSearch, graphType]);

  const canSubmit = useMemo(
    () => username.trim().length >= 3 && password.length >= 4 && !isSubmitting,
    [isSubmitting, password.length, username]
  );

  const canSendQuery = query.trim().length >= 8 && !isQueryLoading;

  const graphLayout = useMemo(() => {
    if (!graphData) {
      return { nodes: [], edges: [], selectedNode: null, typeLabels: [] };
    }

    const width = 1180;
    const height = 680;
    const centerX = width / 2;
    const centerY = height / 2;
    const typeOrder = ["Process", "Material", "Equipment", "Experiment", "Publication", "Expert", "Facility", "Property"];
    const typeColors = new Map([
      ["Process", "#8f2e3b"],
      ["Material", "#0f4f5a"],
      ["Equipment", "#596b2f"],
      ["Experiment", "#7a4e20"],
      ["Publication", "#40517a"],
      ["Expert", "#6b3d6f"],
      ["Facility", "#2f665c"],
      ["Property", "#7b5b18"]
    ]);
    const normalizedSearch = graphSearch.trim().toLowerCase();
    const selectedId = selectedGraphNodeId ?? graphData.nodes[0]?.id ?? null;
    const neighborIds = new Set<string>();

    graphData.edges.forEach((edge) => {
      if (edge.source === selectedId) neighborIds.add(edge.target);
      if (edge.target === selectedId) neighborIds.add(edge.source);
    });

    const nodes = graphData.nodes.map((node, index) => {
      const typeIndex = Math.max(typeOrder.indexOf(node.type), 0);
      const ring = 1 + (typeIndex % 3) * 0.22;
      const angle =
        (2 * Math.PI * index) / Math.max(graphData.nodes.length, 1) -
        Math.PI / 2 +
        typeIndex * 0.23;
      const radius = Math.min(linkDistance * ring, 315);
      const isSelected = node.id === selectedId;
      const isNeighbor = neighborIds.has(node.id);
      const isMatched =
        normalizedSearch.length > 0 &&
        `${node.label} ${node.type} ${node.description}`.toLowerCase().includes(normalizedSearch);
      const isDimmed = Boolean(selectedId) && !isSelected && !isNeighbor;

      return {
        ...node,
        color: typeColors.get(node.type) ?? "#33413d",
        isDimmed,
        isMatched,
        isNeighbor,
        isSelected,
        x: centerX + radius * Math.cos(angle),
        y: centerY + radius * Math.sin(angle)
      };
    });
    const byId = new Map(nodes.map((node) => [node.id, node]));
    const edges = graphData.edges
      .map((edge) => ({
        ...edge,
        sourceNode: byId.get(edge.source),
        targetNode: byId.get(edge.target),
        isActive: edge.source === selectedId || edge.target === selectedId
      }))
      .filter((edge) => edge.sourceNode && edge.targetNode);
    const typeLabels = Array.from(new Set(nodes.map((node) => node.type))).sort();
    const selectedNode = selectedId ? byId.get(selectedId) ?? null : null;

    return { nodes, edges, selectedNode, typeLabels };
  }, [graphData, graphSearch, linkDistance, selectedGraphNodeId]);

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
    setGraphData(null);
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
              <strong>{graphData ? graphData.edges.length : "3-4 уровня"}</strong>
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
                    <span>Embedding: {queryResult.embeddingDimension}</span>
                    <span>Updated: {new Date(queryResult.updatedAt).toLocaleString("ru-RU")}</span>
                  </div>
                  {queryResult.qdrantResults?.length ? (
                    <div className="source-list">
                      {queryResult.qdrantResults.map((result) => (
                        <div className="source-item" key={result.id}>
                          <strong>{result.title}</strong>
                          <span>{Math.round(result.score * 100)}% · {result.domain} · {result.year}</span>
                        </div>
                      ))}
                    </div>
                  ) : null}
                  {queryResult.verifiedFacts?.length ? (
                    <div className="source-list">
                      {queryResult.verifiedFacts.slice(0, 5).map((fact, index) => (
                        <div className="source-item" key={`${fact.id ?? index}`}>
                          <strong>{String(fact.statement ?? fact.text ?? fact.claim ?? fact.subject ?? "Проверенный факт")}</strong>
                          <span>
                            {String(fact.nli_status ?? fact.status ?? "n/a")} · score {String(fact.nli_score ?? fact.score ?? "n/a")}
                          </span>
                        </div>
                      ))}
                    </div>
                  ) : null}
                  {queryResult.warnings?.length ? (
                    <div className="source-list">
                      {queryResult.warnings.map((warning) => (
                        <div className="source-item warning-item" key={warning}>
                          <strong>Warning</strong>
                          <span>{warning}</span>
                        </div>
                      ))}
                    </div>
                  ) : null}
                </>
              ) : (
                <p>
                  Здесь появится структурированный ответ от Java API. Запрос будет
                  преобразован в embedding, сопоставлен с Qdrant и связан с графом Neo4j.
                </p>
              )}
              <div className="source-row">
                <ShieldCheck size={18} aria-hidden="true" />
                <span>JWT передается в защищенные endpoints Java API.</span>
              </div>
            </article>
          </section>

          <section className="graph-workspace">
            <div className="graph-header">
              <div>
                <span className="eyebrow">Neo4j knowledge graph</span>
                <h2>Граф знаний</h2>
              </div>
              <div className="graph-stats">
                <span>{graphData ? graphData.nodes.length : 0} узлов</span>
                <span>{graphData ? graphData.edges.length : 0} связей</span>
              </div>
            </div>

            <div className="graph-toolbar">
              <label className="graph-search">
                <FileSearch size={18} aria-hidden="true" />
                <input
                  onChange={(event) => setGraphSearch(event.target.value)}
                  placeholder="Найти материал, процесс, эксперимент или публикацию"
                  type="search"
                  value={graphSearch}
                />
              </label>
              <label className="graph-type-filter">
                <span>Тип</span>
                <select onChange={(event) => setGraphType(event.target.value)} value={graphType}>
                  <option value="">Все</option>
                  <option value="Material">Material</option>
                  <option value="Process">Process</option>
                  <option value="Equipment">Equipment</option>
                  <option value="Property">Property</option>
                  <option value="Experiment">Experiment</option>
                  <option value="Publication">Publication</option>
                  <option value="Expert">Expert</option>
                  <option value="Facility">Facility</option>
                </select>
              </label>
              <label className="graph-range">
                <span>Дистанция связей</span>
                <input
                  max="300"
                  min="170"
                  onChange={(event) => setLinkDistance(Number(event.target.value))}
                  type="range"
                  value={linkDistance}
                />
              </label>
            </div>

            {graphData ? (
              <div className="knowledge-graph">
                <div className="graph-canvas">
                  <svg viewBox="0 0 1180 680" role="img" aria-label="Knowledge graph">
                    <defs>
                      <marker
                        id="graph-arrow"
                        markerHeight="8"
                        markerWidth="8"
                        orient="auto"
                        refX="7"
                        refY="4"
                        viewBox="0 0 8 8"
                      >
                        <path d="M0,0 L8,4 L0,8 Z" fill="#8aa09a" />
                      </marker>
                    </defs>
                    {graphLayout.edges.map((edge) => (
                      <g className={edge.isActive ? "graph-link active" : "graph-link"} key={edge.id}>
                        <line
                          className="graph-edge"
                          x1={edge.sourceNode!.x}
                          y1={edge.sourceNode!.y}
                          x2={edge.targetNode!.x}
                          y2={edge.targetNode!.y}
                        />
                        <text
                          className="graph-edge-label"
                          x={(edge.sourceNode!.x + edge.targetNode!.x) / 2}
                          y={(edge.sourceNode!.y + edge.targetNode!.y) / 2}
                        >
                          {edge.label}
                        </text>
                      </g>
                    ))}
                    {graphLayout.nodes.map((node) => (
                      <g
                        className={[
                          "graph-node",
                          node.isSelected ? "active" : "",
                          node.isNeighbor ? "neighbor" : "",
                          node.isMatched ? "matched" : "",
                          node.isDimmed ? "muted" : ""
                        ].join(" ")}
                        key={node.id}
                        onClick={() => setSelectedGraphNodeId(node.id)}
                        role="button"
                        tabIndex={0}
                      >
                        <circle cx={node.x} cy={node.y} r={node.isSelected ? 34 : 27} style={{ fill: node.color }} />
                        <text className="graph-node-type" x={node.x} y={node.y + 4}>{node.type}</text>
                        <text className="graph-node-label" x={node.x} y={node.y + 52}>{node.label}</text>
                        <title>{node.label}: {node.description}</title>
                      </g>
                    ))}
                  </svg>
                </div>

                <div className="graph-footer">
                  <div className="graph-legend">
                    {graphLayout.typeLabels.map((type) => (
                      <span key={type}>{type}</span>
                    ))}
                  </div>
                  {graphLayout.selectedNode ? (
                    <aside className="graph-details">
                      <span>{graphLayout.selectedNode.type}</span>
                      <strong>{graphLayout.selectedNode.label}</strong>
                      <p>{graphLayout.selectedNode.description}</p>
                    </aside>
                  ) : null}
                </div>
              </div>
            ) : (
              <div className="graph-empty">
                {(queryResult?.graphPath ?? ["Material", "Process", "Equipment", "Result"]).map((node) => (
                  <span key={node}>{node}</span>
                ))}
              </div>
            )}
            {graphError ? <p className="error-message">{graphError}</p> : null}
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

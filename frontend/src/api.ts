const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

export type AuthPayload = {
  username: string;
  password: string;
};

export type AuthResponse = {
  token: string;
  username: string;
  role: string;
};

export type QdrantSearchResult = {
  id: string;
  score: number;
  title: string;
  text: string;
  domain: string;
  geography: string;
  sourceType: string;
  year: string;
};

export type ResearchQueryRequest = {
  query: string;
  filters: Record<string, string>;
};

export type ResearchQueryResponse = {
  requestId: string;
  status: string;
  answer: string;
  confidence: number;
  updatedAt: string;
  embeddingDimension: number;
  qdrantResults: QdrantSearchResult[];
  graphPath: string[];
  sources: Array<Record<string, unknown>>;
  verifiedFacts: Array<Record<string, unknown>>;
  warnings: string[];
};

export type KnowledgeUploadResponse = {
  requestId: string;
  status: string;
  bucket: string;
  objectName: string;
  originalFilename: string;
  size: number;
  contentType: string;
  uploadedAt: string;
};

export type KnowledgeGraphNode = {
  id: string;
  label: string;
  type: string;
  description: string;
};

export type KnowledgeGraphEdge = {
  id: string;
  source: string;
  target: string;
  type: string;
  label: string;
};

export type KnowledgeGraphResponse = {
  nodes: KnowledgeGraphNode[];
  edges: KnowledgeGraphEdge[];
};

async function parseResponse(response: Response) {
  const contentType = response.headers.get("content-type") ?? "";

  if (contentType.includes("application/json")) {
    return response.json();
  }

  return response.text();
}

export async function authRequest(
  mode: "login" | "register",
  payload: AuthPayload
): Promise<AuthResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/${mode}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  const data = await parseResponse(response);

  if (!response.ok) {
    const message =
      typeof data === "string" && data.trim().length > 0
        ? data
        : "Сервер отклонил запрос. Проверьте логин и пароль.";
    throw new Error(message);
  }

  return data as AuthResponse;
}

export async function ping(token?: string) {
  const response = await fetch(`${API_BASE_URL}/ping`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined
  });

  if (!response.ok) {
    throw new Error("Не удалось проверить доступность сервера.");
  }

  return response.text();
}

export async function sendResearchQuery(
  token: string,
  payload: ResearchQueryRequest
): Promise<ResearchQueryResponse> {
  const response = await fetch(`${API_BASE_URL}/api/research/query`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  const data = await parseResponse(response);

  if (!response.ok) {
    const message =
      typeof data === "string" && data.trim().length > 0
        ? data
        : "Не удалось обработать исследовательский запрос.";
    throw new Error(message);
  }

  return data as ResearchQueryResponse;
}

export async function uploadKnowledgeFile(
  token: string,
  file: File
): Promise<KnowledgeUploadResponse> {
  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch(`${API_BASE_URL}/api/knowledge-base/upload`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`
    },
    body: formData
  });

  const data = await parseResponse(response);

  if (!response.ok) {
    const message =
      typeof data === "string" && data.trim().length > 0
        ? data
        : "Не удалось загрузить файл в базу знаний.";
    throw new Error(message);
  }

  return data as KnowledgeUploadResponse;
}

export type KnowledgeGraphParams = {
  search?: string;
  type?: string;
  depth?: number;
  limit?: number;
};

export async function getKnowledgeGraph(
  token: string,
  params: KnowledgeGraphParams = {}
): Promise<KnowledgeGraphResponse> {
  const searchParams = new URLSearchParams();
  if (params.search) searchParams.set("search", params.search);
  if (params.type) searchParams.set("type", params.type);
  if (params.depth) searchParams.set("depth", String(params.depth));
  if (params.limit) searchParams.set("limit", String(params.limit));
  const query = searchParams.toString();

  const response = await fetch(`${API_BASE_URL}/api/graph/knowledge${query ? `?${query}` : ""}`, {
    headers: {
      Authorization: `Bearer ${token}`
    }
  });

  const data = await parseResponse(response);

  if (!response.ok) {
    const message =
      typeof data === "string" && data.trim().length > 0
        ? data
        : "Не удалось загрузить граф знаний.";
    throw new Error(message);
  }

  return data as KnowledgeGraphResponse;
}

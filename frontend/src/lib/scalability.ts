// Клиентские хранилища для функций масштабирования DocHelper.
// Данные живут в localStorage — как и профиль, подпись, список документов.

export interface CorporateTemplate {
  id: string;
  title: string;
  description: string;
  baseTemplateId: "standard" | "modern";
  accentColor: string;
  logoDataUrl?: string;
  createdAt: string;
}

export interface CustomDocType {
  id: string;
  label: string;
  baseTypeId: "memo" | "report" | "certificate" | "letter";
  createdAt: string;
}

export interface SedIntegration {
  systemName: string;
  apiUrl: string;
  apiKey: string;
  configuredAt: string;
}

interface DocEntry {
  id: string;
  name: string;
  type: string;
  date: string;
  sedSentAt?: string;
}

const KEYS = {
  templates: "dochelper:corporate-templates",
  docTypes: "dochelper:custom-doc-types",
  sed: "dochelper:sed-integration",
  documents: "dochelper_documents",
} as const;

function isBrowser(): boolean {
  return typeof window !== "undefined" && typeof window.localStorage !== "undefined";
}

function safeParse<T>(raw: string | null, fallback: T): T {
  if (!raw) return fallback;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

function newId(): string {
  if (isBrowser() && "randomUUID" in crypto) return crypto.randomUUID();
  return `id-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

// --- Корпоративные шаблоны ---

export function getCorporateTemplates(): CorporateTemplate[] {
  if (!isBrowser()) return [];
  return safeParse(localStorage.getItem(KEYS.templates), []);
}

export function addCorporateTemplate(
  input: Omit<CorporateTemplate, "id" | "createdAt">,
): CorporateTemplate[] {
  const item: CorporateTemplate = { ...input, id: newId(), createdAt: new Date().toISOString() };
  const next = [...getCorporateTemplates(), item];
  localStorage.setItem(KEYS.templates, JSON.stringify(next));
  return next;
}

export function removeCorporateTemplate(id: string): CorporateTemplate[] {
  const next = getCorporateTemplates().filter((t) => t.id !== id);
  localStorage.setItem(KEYS.templates, JSON.stringify(next));
  return next;
}

// --- Пользовательские типы документов ---

export function getCustomDocTypes(): CustomDocType[] {
  if (!isBrowser()) return [];
  return safeParse(localStorage.getItem(KEYS.docTypes), []);
}

export function addCustomDocType(
  input: Omit<CustomDocType, "id" | "createdAt">,
): CustomDocType[] {
  const item: CustomDocType = { ...input, id: newId(), createdAt: new Date().toISOString() };
  const next = [...getCustomDocTypes(), item];
  localStorage.setItem(KEYS.docTypes, JSON.stringify(next));
  return next;
}

export function removeCustomDocType(id: string): CustomDocType[] {
  const next = getCustomDocTypes().filter((t) => t.id !== id);
  localStorage.setItem(KEYS.docTypes, JSON.stringify(next));
  return next;
}

// --- Интеграция с СЭД ---

export function getSedIntegration(): SedIntegration | null {
  if (!isBrowser()) return null;
  return safeParse(localStorage.getItem(KEYS.sed), null);
}

export function saveSedIntegration(input: Omit<SedIntegration, "configuredAt">): SedIntegration {
  const cfg: SedIntegration = { ...input, configuredAt: new Date().toISOString() };
  localStorage.setItem(KEYS.sed, JSON.stringify(cfg));
  return cfg;
}

export function clearSedIntegration(): void {
  if (!isBrowser()) return;
  localStorage.removeItem(KEYS.sed);
}

// --- Отправка документа в СЭД (демонстрационная, но по-настоящему сохраняемая отметка) ---

export function markDocumentSentToSed(documentId: string): DocEntry[] {
  if (!isBrowser()) return [];
  const docs: DocEntry[] = safeParse(localStorage.getItem(KEYS.documents), []);
  const next = docs.map((d) =>
    d.id === documentId ? { ...d, sedSentAt: new Date().toISOString() } : d,
  );
  localStorage.setItem(KEYS.documents, JSON.stringify(next));
  return next;
}

import { useState, useEffect } from "react";
import { getSedIntegration, markDocumentSentToSed, type SedIntegration } from "../lib/scalability";

interface DocEntry {
  id: string;
  name: string;
  type: string;
  date: string;
  sedSentAt?: string;
}

const STORAGE_KEY = "dochelper_documents";

const TYPE_LABELS: Record<string, string> = {
  memo: "Служебная записка",
  report: "Докладная записка",
  certificate: "Информационная справка",
  letter: "Письмо",
};

function loadDocuments(): DocEntry[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) return JSON.parse(raw);
  } catch { /* ignore */ }
  return [];
}

function removeDocument(id: string) {
  const docs = loadDocuments().filter((d) => d.id !== id);
  localStorage.setItem(STORAGE_KEY, JSON.stringify(docs));
  return docs;
}

export default function DocumentsList() {
  const [docs, setDocs] = useState<DocEntry[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [sed, setSed] = useState<SedIntegration | null>(null);

  useEffect(() => {
    setDocs(loadDocuments());
    setSed(getSedIntegration());
    setLoaded(true);
  }, []);

  const handleDelete = (id: string) => {
    const updated = removeDocument(id);
    setDocs(updated);
  };

  const handleSendToSed = (id: string) => {
    const updated = markDocumentSentToSed(id);
    setDocs(updated);
  };

  if (!loaded) {
    return (
      <div className="space-y-3">
        {[1, 2, 3].map((i) => (
          <div key={i} className="flex items-center gap-3 rounded-xl border border-line bg-white p-4">
            <div className="skeleton h-10 w-10 shrink-0 rounded-lg" />
            <div className="flex-1 space-y-2">
              <div className="skeleton h-4 w-40" />
              <div className="skeleton h-3 w-28" />
            </div>
          </div>
        ))}
      </div>
    );
  }

  if (docs.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center animate-fade-in">
        <div className="flex h-20 w-20 items-center justify-center rounded-2xl bg-accent-50 text-accent-600">
          <svg width="36" height="36" viewBox="0 0 36 36" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
            <path d="M8 6H20L26 12V28C26 29.1 25.1 30 24 30H8C6.9 30 6 29.1 6 28V8C6 6.9 6.9 6 8 6Z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
            <path d="M19 6V12H25" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
            <path d="M12 20H22M12 24H18" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
        </div>
        <h3 className="mt-4 text-base font-medium text-ink-900">Нет документов</h3>
        <p className="mt-1 text-sm text-ink-600">Создайте первый документ, и он появится здесь.</p>
        <a
          href="/"
          className="btn-press mt-5 inline-flex items-center gap-2 rounded-xl bg-accent-600 px-5 py-2.5 text-sm font-medium text-white transition-colors hover:bg-accent-500"
        >
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M7 2.5V11.5M2.5 7H11.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
          Создать документ
        </a>
      </div>
    );
  }

  return (
    <div className="stagger-children space-y-2">
      {docs.map((doc) => (
        <div
          key={doc.id}
          className="group flex items-center gap-3 rounded-xl border border-line bg-white p-4 transition-all duration-200 hover:border-accent-100 hover:shadow-sm"
        >
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-accent-50 text-accent-600 transition-colors group-hover:bg-accent-100">
            <svg width="18" height="18" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
              <path d="M4 2H9.5L12.5 5V13.5C12.5 14.05 12.05 14.5 11.5 14.5H4.5C3.95 14.5 3.5 14.05 3.5 13.5V2.5C3.5 1.95 3.95 2 4 2Z" stroke="currentColor" strokeWidth="1.3" strokeLinejoin="round" />
              <path d="M9.3 2V5H12.3" stroke="currentColor" strokeWidth="1.3" strokeLinejoin="round" />
            </svg>
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-ink-900">{doc.name}</p>
            <p className="text-xs text-ink-400">{TYPE_LABELS[doc.type] || doc.type} · {doc.date}</p>
          </div>
          {doc.sedSentAt ? (
            <span className="flex shrink-0 items-center gap-1.5 rounded-full bg-success-50 px-2.5 py-1 text-xs font-medium text-success-600">
              <svg width="12" height="12" viewBox="0 0 16 16" fill="none">
                <circle cx="8" cy="8" r="7" fill="currentColor" fillOpacity="0.2" />
                <path d="M5 8.2L7.1 10.3L11.2 5.9" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
              Отправлено в СЭД
            </span>
          ) : sed ? (
            <button
              type="button"
              onClick={() => handleSendToSed(doc.id)}
              className="shrink-0 rounded-lg border border-line px-3 py-1.5 text-xs font-medium text-ink-600 transition-colors hover:border-accent-200 hover:bg-accent-50 hover:text-accent-600"
            >
              Отправить в СЭД
            </button>
          ) : null}
          <div className="flex shrink-0 items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
            <button
              type="button"
              onClick={() => handleDelete(doc.id)}
              aria-label="Удалить"
              className="flex h-8 w-8 items-center justify-center rounded-lg text-ink-400 transition-colors hover:bg-danger-50 hover:text-danger-500"
            >
              <svg width="15" height="15" viewBox="0 0 14 14" fill="none">
                <path d="M3.5 3.5L10.5 10.5M10.5 3.5L3.5 10.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
              </svg>
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}

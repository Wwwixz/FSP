import { useCallback, useEffect, useState } from "react";
import { getSedIntegration, type SedIntegration } from "../lib/scalability";

interface DocEntry {
  id: string;
  fileName: string;
  documentType: string;
  templateId: string;
  createdAt: string;
  downloadUrl: string;
}

const TYPE_LABELS: Record<string, string> = {
  memo: "Служебная записка",
  report: "Докладная записка",
  certificate: "Информационная справка",
  letter: "Письмо",
};

const TEMPLATE_LABELS: Record<string, string> = {
  standard: "Классический",
  modern: "Современный",
  custom: "Свой шаблон",
  uploaded: "Бланк организации",
};

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleString("ru-RU", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return iso;
  }
}

/**
 * «Мои документы»: все сформированные файлы хранятся на сервере
 * (in-memory, согласно ТЗ) — доступны для просмотра, скачивания,
 * выгрузки архивом и отправки в подключённую СЭД.
 */
export default function DocumentsList() {
  const [docs, setDocs] = useState<DocEntry[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [previewDoc, setPreviewDoc] = useState<DocEntry | null>(null);
  const [sed, setSed] = useState<SedIntegration | null>(null);
  const [sendingId, setSendingId] = useState<string | null>(null);
  const [sedSent, setSedSent] = useState<Set<string>>(new Set());
  const [sedMsg, setSedMsg] = useState<{ text: string; err?: boolean } | null>(null);

  const refresh = useCallback(async () => {
    try {
      const res = await fetch("/api/documents");
      if (!res.ok) {
        setError("Не удалось загрузить список документов");
        return;
      }
      const json = await res.json();
      setDocs(Array.isArray(json) ? (json as DocEntry[]) : []);
      setError(null);
    } catch {
      setError("Сервис недоступен — запустите бэкенд и обновите страницу");
    } finally {
      setLoaded(true);
    }
  }, []);

  useEffect(() => {
    refresh();
    setSed(getSedIntegration());
  }, [refresh]);

  // Закрытие просмотра по Escape
  useEffect(() => {
    if (!previewDoc) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setPreviewDoc(null);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [previewDoc]);

  const sendToSed = async (doc: DocEntry) => {
    if (!sed || sendingId) return;
    setSendingId(doc.id);
    setSedMsg(null);
    try {
      const res = await fetch(`/api/documents/${doc.id}/send-to-sed`, {
        method: "POST",
        headers: { "Content-Type": "application/json; charset=utf-8" },
        body: JSON.stringify({ apiUrl: sed.apiUrl, apiKey: sed.apiKey, systemName: sed.systemName }),
      });
      const json = await res.json();
      if (!res.ok || !json?.success) {
        setSedMsg({ text: json?.error?.message ?? "Не удалось отправить в СЭД", err: true });
        return;
      }
      setSedSent((prev) => new Set(prev).add(doc.id));
      setSedMsg({ text: json.message ?? `Документ «${doc.fileName}» отправлен в СЭД` });
    } catch {
      setSedMsg({ text: "СЭД недоступна — проверьте адрес в настройках", err: true });
    } finally {
      setSendingId(null);
    }
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

  if (error) {
    return (
      <div className="rounded-xl border border-warning-100 bg-warning-50 p-5 text-sm text-ink-900">
        {error}
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
        <h3 className="mt-4 text-base font-medium text-ink-900">Пока нет документов</h3>
        <p className="mt-1 text-sm text-ink-600">
          Создайте документ — он сохранится здесь, и его можно будет скачать повторно.
        </p>
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
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-accent-100 bg-accent-50/50 px-4 py-3">
        <p className="text-sm text-ink-900">
          Документов: <span className="font-semibold">{docs.length}</span> — все хранятся на сервере
        </p>
        <a
          href="/api/documents/archive"
          download
          className="rounded-lg bg-accent-600 px-4 py-2 text-xs font-medium text-white transition-colors hover:bg-accent-500"
          title="Все файлы одним ZIP-архивом со списком"
        >
          🗂 Скачать всё архивом (ZIP)
        </a>
      </div>

      {sedMsg && (
        <div
          className={[
            "rounded-xl px-4 py-3 text-sm",
            sedMsg.err ? "border border-danger-100 bg-danger-50 text-danger-600" : "border border-success-100 bg-success-50 text-success-600",
          ].join(" ")}
        >
          {sedMsg.text}
        </div>
      )}

      {docs.map((doc) => {
        const isPdf = doc.fileName.toLowerCase().endsWith(".pdf");
        return (
          <div
            key={doc.id}
            className="group flex flex-wrap items-center gap-3 rounded-xl border border-line bg-white p-4 transition-all duration-200 hover:border-accent-100 hover:shadow-sm"
          >
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-accent-50 text-accent-600 transition-colors group-hover:bg-accent-100">
              {isPdf ? (
                <svg width="18" height="18" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                  <path d="M4 2H9.5L12.5 5V13.5C12.5 14.05 12.05 14.5 11.5 14.5H4.5C3.95 14.5 3.5 14.05 3.5 13.5V2.5C3.5 1.95 3.95 2 4 2Z" stroke="currentColor" strokeWidth="1.3" strokeLinejoin="round" />
                  <path d="M9.3 2V5H12.3" stroke="currentColor" strokeWidth="1.3" strokeLinejoin="round" />
                  <path d="M6 9.5H7.2C7.9 9.5 8.3 9.1 8.3 8.5C8.3 7.9 7.9 7.5 7.2 7.5H6V11.5" stroke="currentColor" strokeWidth="0.9" strokeLinecap="round" />
                </svg>
              ) : (
                <svg width="18" height="18" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <path d="M4 2H9.5L12.5 5V13.5C12.5 14.05 12.05 14.5 11.5 14.5H4.5C3.95 14.5 3.5 14.05 3.5 13.5V2.5C3.5 1.95 3.95 2 4 2Z" stroke="currentColor" strokeWidth="1.3" strokeLinejoin="round" />
                  <path d="M9.3 2V5H12.3" stroke="currentColor" strokeWidth="1.3" strokeLinejoin="round" />
                </svg>
              )}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-ink-900">{doc.fileName}</p>
              <p className="text-xs text-ink-400">
                {TYPE_LABELS[doc.documentType] || doc.documentType} ·{" "}
                {TEMPLATE_LABELS[doc.templateId] || doc.templateId} · {formatDate(doc.createdAt)}
              </p>
            </div>
            <div className="flex shrink-0 flex-wrap items-center gap-2">
              {sedSent.has(doc.id) ? (
                <span className="flex items-center gap-1.5 rounded-full bg-success-50 px-2.5 py-1 text-xs font-medium text-success-600">
                  <svg width="12" height="12" viewBox="0 0 16 16" fill="none">
                    <circle cx="8" cy="8" r="7" fill="currentColor" fillOpacity="0.2" />
                    <path d="M5 8.2L7.1 10.3L11.2 5.9" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                  В СЭД
                </span>
              ) : (
                sed && (
                  <button
                    type="button"
                    onClick={() => sendToSed(doc)}
                    disabled={sendingId === doc.id}
                    className="rounded-lg border border-line px-3 py-1.5 text-xs font-medium text-ink-600 transition-colors hover:border-accent-200 hover:bg-accent-50 hover:text-accent-600"
                    title={`Отправить в ${sed.systemName || "СЭД"}`}
                  >
                    {sendingId === doc.id ? "Отправка…" : "В СЭД"}
                  </button>
                )
              )}
              <button
                type="button"
                onClick={() => setPreviewDoc(doc)}
                className="rounded-lg border border-line px-3 py-1.5 text-xs font-medium text-ink-600 transition-colors hover:bg-surface"
                title="Посмотреть документ прямо на сайте"
              >
                Просмотр
              </button>
              <a
                href={doc.downloadUrl}
                download={doc.fileName}
                className="rounded-lg bg-accent-600 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-accent-500"
              >
                Скачать
              </a>
            </div>
          </div>
        );
      })}

      {previewDoc && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-ink-900/60 p-4 backdrop-blur-sm"
          onClick={() => setPreviewDoc(null)}
        >
          <div
            className="flex h-full max-h-[92vh] w-full max-w-4xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold text-ink-900">{previewDoc.fileName}</p>
                <p className="text-xs text-ink-400">
                  {TYPE_LABELS[previewDoc.documentType] || previewDoc.documentType} ·{" "}
                  {formatDate(previewDoc.createdAt)}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <a
                  href={`/api/documents/${previewDoc.id}/preview`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="rounded-lg border border-line px-3 py-1.5 text-xs font-medium text-ink-600 transition-colors hover:bg-surface"
                >
                  В новой вкладке
                </a>
                <a
                  href={previewDoc.downloadUrl}
                  download={previewDoc.fileName}
                  className="rounded-lg bg-accent-600 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-accent-500"
                >
                  Скачать
                </a>
                <button
                  type="button"
                  onClick={() => setPreviewDoc(null)}
                  aria-label="Закрыть просмотр"
                  className="flex h-8 w-8 items-center justify-center rounded-lg text-ink-400 transition-colors hover:bg-danger-50 hover:text-danger-500"
                >
                  <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                    <path d="M3.5 3.5L10.5 10.5M10.5 3.5L3.5 10.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
                  </svg>
                </button>
              </div>
            </div>
            <iframe
              src={`/api/documents/${previewDoc.id}/preview`}
              title={`Просмотр: ${previewDoc.fileName}`}
              className="h-full w-full flex-1 bg-surface"
            />
          </div>
        </div>
      )}
    </div>
  );
}

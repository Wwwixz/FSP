import { useState } from "react";

interface PreviewDocumentProps {
  downloadUrl?: string;
  fileName?: string;
  onDownload?: () => void;
  onDownloadPdf?: () => void;
  isGeneratingPdf?: boolean;
  documentId?: string;
  qrDataUrl?: string | null;
  previewUrl?: string | null;
  warnings?: string[];
}

/**
 * Предпросмотр готового документа: показывает НАСТОЯЩИЙ сгенерированный файл
 * (через /preview — тот же DOCX, что скачивается), а не HTML-макет.
 * Что видишь в окне — то и в файле.
 */
export default function PreviewDocument({
  downloadUrl,
  fileName = "document.docx",
  onDownload,
  onDownloadPdf,
  isGeneratingPdf = false,
  documentId,
  qrDataUrl,
  previewUrl,
  warnings = [],
}: PreviewDocumentProps) {
  const [mailOpen, setMailOpen] = useState(false);
  const [mailTo, setMailTo] = useState("");
  const [mailState, setMailState] = useState<{ text: string; err?: boolean; busy?: boolean } | null>(null);

  const realDownload = downloadUrl ?? undefined;

  const sendEmail = async () => {
    if (!documentId) return;
    setMailState({ text: "Отправляем…", busy: true });
    try {
      const res = await fetch(`/api/documents/${documentId}/email`, {
        method: "POST",
        headers: { "Content-Type": "application/json; charset=utf-8" },
        body: JSON.stringify({ to: mailTo.trim() }),
      });
      const json = await res.json();
      if (!res.ok || !json?.success) {
        setMailState({ text: json?.error?.message ?? "Не удалось отправить письмо", err: true });
        return;
      }
      setMailState({ text: json.message ?? "Письмо отправлено" });
    } catch {
      setMailState({ text: "Сервис почты недоступен — попробуйте позже", err: true });
    }
  };

  return (
    <div className="rounded-2xl border border-line bg-card p-6 shadow-sm shadow-ink-900/[0.03]">
      <h1 className="text-xl font-semibold text-ink-900">Готовый документ</h1>

      <div className="mt-4 flex items-start gap-2.5 rounded-xl bg-success-50 p-4 text-sm text-ink-900">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" className="mt-0.5 shrink-0 text-success-500" aria-hidden="true">
          <circle cx="8" cy="8" r="7" fill="currentColor" fillOpacity="0.15" />
          <path d="M5 8.2L7.1 10.3L11.2 5.9" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <div>
          <p>
            <span className="font-medium">Документ успешно подготовлен!</span> Ниже — сам файл
            один в один, каким он будет при скачивании.
          </p>
          {warnings.length > 0 && (
            <ul className="mt-2 space-y-1 text-xs text-amber-700">
              {warnings.map((w, i) => <li key={i}>⚠ {w}</li>)}
            </ul>
          )}
        </div>
      </div>

      <div className="mt-4 overflow-hidden rounded-xl border border-line bg-surface">
        <div className="flex items-center justify-between bg-navy-900 px-4 py-2 text-sm text-white">
          <span className="truncate">{fileName}</span>
          {realDownload && (
            <a href={realDownload} download={fileName} className="shrink-0 text-xs text-white/70 transition-colors hover:text-white">
              Скачать ⬇
            </a>
          )}
        </div>
        {documentId ? (
          <iframe
            src={`/api/documents/${documentId}/preview`}
            title={`Просмотр: ${fileName}`}
            className="h-[640px] w-full bg-white"
          />
        ) : (
          <div className="p-8 text-sm text-ink-500">
            Документ появится здесь сразу после генерации.
          </div>
        )}
      </div>

      {realDownload && (
        <div className="mt-5 space-y-4">
          <div className="flex flex-wrap items-center justify-end gap-3">
            <button
              type="button"
              onClick={() => window.open(`/api/documents/${documentId}/envelope`, "_blank")}
              disabled={!documentId}
              className="inline-flex items-center gap-2 rounded-lg border border-line bg-white px-5 py-2.5 text-sm font-medium text-ink-900 transition-colors hover:bg-surface disabled:opacity-40"
              title="Печатный конверт E65 с адресатом и отправителем из реквизитов"
            >
              ✉ Печатный конверт
            </button>
            <button
              type="button"
              onClick={() => setMailOpen((v) => !v)}
              disabled={!documentId}
              className="inline-flex items-center gap-2 rounded-lg border border-line bg-white px-5 py-2.5 text-sm font-medium text-ink-900 transition-colors hover:bg-surface disabled:opacity-40"
              title="Отправить файл вложением на почту"
            >
              ✉ Отправить по почте
            </button>
            <button
              type="button"
              onClick={onDownloadPdf}
              disabled={isGeneratingPdf}
              title="Сгенерировать PDF из этого документа и скачать"
              className={[
                "inline-flex items-center gap-2 rounded-lg border px-5 py-2.5 text-sm font-medium transition-colors",
                isGeneratingPdf
                  ? "cursor-wait border-line bg-surface text-ink-400"
                  : "border-line bg-white text-ink-900 hover:bg-surface",
              ].join(" ")}
            >
              {isGeneratingPdf ? (
                "⏳ Готовим PDF…"
              ) : (
                <>
                  <svg width="15" height="15" viewBox="0 0 14 14" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                    <path d="M7 1.5V9" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
                    <path d="M4 6.5L7 9.5L10 6.5" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round" />
                    <path d="M2.5 11.5H11.5" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
                  </svg>
                  Скачать PDF
                </>
              )}
            </button>
            <a
              href={realDownload}
              download={fileName}
              onClick={() => onDownload?.()}
              className="inline-flex items-center gap-2 rounded-lg bg-accent-600 px-5 py-2.5 text-sm font-medium text-white transition-colors hover:bg-accent-500"
            >
              <svg width="15" height="15" viewBox="0 0 14 14" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                <path d="M7 1.5V9" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
                <path d="M4 6.5L7 9.5L10 6.5" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M2.5 11.5H11.5" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
              </svg>
              Скачать Word ({fileName})
            </a>
          </div>

          {mailOpen && (
            <div className="rounded-xl border border-line bg-surface/60 p-4">
              <div className="flex flex-wrap items-center gap-3">
                <input
                  type="email"
                  value={mailTo}
                  onChange={(e) => {
                    setMailTo(e.target.value);
                    setMailState(null);
                  }}
                  placeholder="адрес получателя, например ivanova@example.ru"
                  className="min-w-64 flex-1 rounded-lg border border-line bg-white px-3.5 py-2.5 text-sm outline-none focus:border-accent-500"
                />
                <button
                  type="button"
                  onClick={sendEmail}
                  disabled={mailState?.busy || !mailTo.trim()}
                  className={[
                    "rounded-lg px-4 py-2.5 text-sm font-medium text-white transition-colors",
                    mailState?.busy || !mailTo.trim() ? "bg-ink-300 cursor-not-allowed" : "bg-accent-600 hover:bg-accent-500",
                  ].join(" ")}
                >
                  {mailState?.busy ? "Отправляем…" : "Отправить файл"}
                </button>
              </div>
              {mailState && !mailState.busy && (
                <p className={["mt-2 text-xs", mailState.err ? "text-danger-500" : "text-success-600"].join(" ")}>
                  {mailState.text}
                </p>
              )}
            </div>
          )}

          {qrDataUrl && previewUrl && (
            <div className="flex flex-wrap items-center gap-4 rounded-xl border border-line bg-surface/60 p-4">
              <img src={qrDataUrl} alt="QR-код проверки подлинности" className="h-24 w-24 rounded-lg border border-line bg-white p-1" />
              <div className="min-w-0 text-xs leading-relaxed text-ink-600">
                <p className="text-sm font-medium text-ink-900">QR-код вшит в документ</p>
                <p className="mt-1">
                  Наведите камеру телефона — документ откроется в браузере.{" "}
                  <a href={previewUrl} target="_blank" rel="noopener noreferrer" className="text-accent-600 underline">
                    Проверить ссылку
                  </a>
                </p>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

import { type ReactNode, useEffect, useMemo, useState } from "react";
import type { RequisiteDto } from "../../types/wizard";

interface PreviewDocumentProps {
  improvedText: string;
  documentTypeLabel: string;
  requisites: RequisiteDto;
  downloadUrl?: string;
  fileName?: string;
  onDownload?: () => void;
  warnings?: string[];
}

function splitParagraphs(text: string): string[] {
  return text.split(/\n+/).map((p) => p.trim()).filter(Boolean);
}

function getStoredImage(key: string): string | null {
  if (typeof window === "undefined") return null;
  try {
    const v = window.localStorage.getItem(key);
    return v && v.trim() ? v : null;
  } catch {
    return null;
  }
}

function ToolbarIconButton({
  children,
  label,
  onClick,
}: {
  children: ReactNode;
  label: string;
  onClick?: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className="flex h-7 w-7 items-center justify-center rounded-md text-white/70 transition-colors hover:bg-white/10 hover:text-white"
    >
      {children}
    </button>
  );
}

function P(val: unknown, fallback = "[Заполнить]") {
  if (val == null) return fallback;
  const s = String(val).trim();
  return s.length > 0 ? s : fallback;
}

export default function PreviewDocument({
  improvedText,
  documentTypeLabel,
  requisites,
  downloadUrl,
  fileName = "document.docx",
  onDownload,
  warnings = [],
}: PreviewDocumentProps) {
  const [zoom, setZoom] = useState(100);
  const [sigImg, setSigImg] = useState<string | null>(null);
  const [photoImg, setPhotoImg] = useState<string | null>(null);
  const paragraphs = useMemo(() => splitParagraphs(improvedText), [improvedText]);

  useEffect(() => {
    setSigImg(getStoredImage("dochelper:signature-image"));
    setPhotoImg(getStoredImage("dochelper:photo-image"));
    // Синхронизируем при смене вкладок/возврате
    const onFocus = () => {
      setSigImg(getStoredImage("dochelper:signature-image"));
      setPhotoImg(getStoredImage("dochelper:photo-image"));
    };
    window.addEventListener("focus", onFocus);
    return () => window.removeEventListener("focus", onFocus);
  }, []);

  const realDownload = downloadUrl ?? undefined;

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
            <span className="font-medium">Документ успешно подготовлен!</span> Файл
            соответствует выбранному шаблону и содержит все необходимые реквизиты.
          </p>
          {warnings.length > 0 && (
            <ul className="mt-2 space-y-1 text-xs text-amber-700">
              {warnings.map((w, i) => <li key={i}>⚠ {w}</li>)}
            </ul>
          )}
        </div>
      </div>

      <div className="mt-4 overflow-hidden rounded-xl border border-line">
        <div className="flex items-center justify-between bg-navy-900 px-4 py-2 text-sm text-white">
          <span className="truncate">{fileName}</span>
          <div className="flex items-center gap-3 text-xs text-white/60">
            <span>1 / 1</span>
            <div className="flex items-center gap-1">
              <ToolbarIconButton label="Уменьшить" onClick={() => setZoom((z) => Math.max(50, z - 10))}>
                −
              </ToolbarIconButton>
              <span className="w-9 text-center text-white/80">{zoom}%</span>
              <ToolbarIconButton label="Увеличить" onClick={() => setZoom((z) => Math.min(150, z + 10))}>
                +
              </ToolbarIconButton>
            </div>
            {realDownload ? (
              <a
                href={realDownload}
                download={fileName}
                onClick={() => onDownload?.()}
                className="flex h-7 w-7 items-center justify-center rounded-md text-white/70 transition-colors hover:bg-white/10 hover:text-white"
                aria-label="Скачать"
                title="Скачать"
              >
                <svg width="14" height="14" viewBox="0 0 14 14" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <path d="M7 1.5V9" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
                  <path d="M4 6.5L7 9.5L10 6.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" />
                  <path d="M2.5 11.5H11.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
                </svg>
              </a>
            ) : (
              <ToolbarIconButton label="Скачать" onClick={() => onDownload?.()}>
                <svg width="14" height="14" viewBox="0 0 14 14" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <path d="M7 1.5V9" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
                  <path d="M4 6.5L7 9.5L10 6.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" />
                  <path d="M2.5 11.5H11.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
                </svg>
              </ToolbarIconButton>
            )}
          </div>
        </div>

        <div className="max-h-[520px] overflow-auto bg-surface p-6">
          <div
            className="mx-auto min-h-[600px] w-full max-w-[480px] origin-top bg-white p-10 text-[13px] leading-relaxed text-ink-900 shadow-sm"
            style={{ transform: `scale(${zoom / 100})` }}
          >
            <div className="flex items-start justify-end gap-4 text-right text-sm">
              {photoImg && (
                <div className="shrink-0 rounded-md border border-line bg-white p-1">
                  <img
                    src={photoImg}
                    alt="Фото"
                    className="h-20 w-16 object-cover rounded"
                  />
                </div>
              )}
              <div className="whitespace-pre-line">
                {P(requisites.recipient, "[Заполнить: Адресат]")}
              </div>
            </div>

            <p className="mt-8 text-center text-sm font-semibold">{documentTypeLabel}</p>

            <dl className="mt-6 space-y-1 text-sm">
              <div className="flex gap-2">
                <dt className="w-28 shrink-0 text-ink-600">От:</dt>
                <dd>{P(requisites.author)}</dd>
              </div>
              <div className="flex gap-2">
                <dt className="w-28 shrink-0 text-ink-600">Дата:</dt>
                <dd>{P(requisites.date, "[Заполнить дату ДД.ММ.ГГГГ]")}</dd>
              </div>
              <div className="flex gap-2">
                <dt className="w-28 shrink-0 text-ink-600">Исх. №:</dt>
                <dd>{P(requisites.number, "[Заполнить номер]")}</dd>
              </div>
            </dl>

            <p className="mt-6 text-sm font-semibold">
              {P(requisites.subject, "[Тема / заголовок]")}
            </p>

            <div className="mt-3 space-y-3 text-sm text-justify">
              {paragraphs.length === 0 && (
                <p className="text-ink-400">[Текст документа]</p>
              )}
              {paragraphs.map((p, i) => <p key={i}>{p}</p>)}
            </div>

            <div className="mt-16 ml-auto w-48 text-right text-sm">
              {sigImg && (
                <div className="flex justify-end">
                  <div className="inline-block max-w-full">
                    <img
                      src={sigImg}
                      alt="Подпись"
                      className="max-h-12 max-w-40 object-contain"
                    />
                  </div>
                </div>
              )}
              <p className={sigImg ? "mt-1" : ""}>{P(requisites.signature)}</p>
            </div>
          </div>
        </div>
      </div>

      {realDownload && (
        <div className="mt-5 flex justify-end">
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
            Скачать {fileName}
          </a>
        </div>
      )}
    </div>
  );
}

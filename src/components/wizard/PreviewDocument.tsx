import { useState, type ReactNode } from "react";
import type { RequisiteValues } from "../../types/wizard";

interface PreviewDocumentProps {
  improvedText: string;
  requisites: RequisiteValues;
  senderName: string;
  onDownload: () => void;
}

function splitParagraphs(text: string): string[] {
  return text.split(/\n+/).map((p) => p.trim()).filter(Boolean);
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

export default function PreviewDocument({
  improvedText,
  requisites,
  senderName,
  onDownload,
}: PreviewDocumentProps) {
  const [zoom, setZoom] = useState(100);
  const paragraphs = splitParagraphs(improvedText);
  const subject = "О рассмотрении вопроса о выделении дополнительного финансирования";

  return (
    <div className="rounded-2xl border border-line bg-card p-6 shadow-sm shadow-ink-900/[0.03]">
      <h1 className="text-xl font-semibold text-ink-900">Готовый документ</h1>

      <div className="mt-4 flex items-start gap-2.5 rounded-xl bg-success-50 p-4 text-sm text-ink-900">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" className="mt-0.5 shrink-0 text-success-500" aria-hidden="true">
          <circle cx="8" cy="8" r="7" fill="currentColor" fillOpacity="0.15" />
          <path d="M5 8.2L7.1 10.3L11.2 5.9" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <p>
          <span className="font-medium">Документ успешно подготовлен!</span> Файл
          соответствует выбранному шаблону и содержит все необходимые
          реквизиты.
        </p>
      </div>

      <div className="mt-4 overflow-hidden rounded-xl border border-line">
        <div className="flex items-center justify-between bg-navy-900 px-4 py-2 text-sm text-white">
          <span className="truncate">Служебная_записка.docx</span>
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
            <ToolbarIconButton label="Обновить">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                <path
                  d="M2.3 7A4.7 4.7 0 0 1 11 4.7M11.7 7A4.7 4.7 0 0 1 3 9.3"
                  stroke="currentColor"
                  strokeWidth="1.2"
                  strokeLinecap="round"
                />
                <path d="M11 2.5V4.7H8.8" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M3 11.5V9.3H5.2" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </ToolbarIconButton>
            <ToolbarIconButton label="Скачать" onClick={onDownload}>
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                <path d="M7 1.5V9" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
                <path d="M4 6.5L7 9.5L10 6.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M2.5 11.5H11.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
              </svg>
            </ToolbarIconButton>
            <ToolbarIconButton label="Печать">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                <rect x="3" y="5" width="8" height="4.5" stroke="currentColor" strokeWidth="1.2" />
                <path d="M4 5V2.5H10V5" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round" />
                <path d="M4 9.5V11.5H10V9.5" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round" />
              </svg>
            </ToolbarIconButton>
          </div>
        </div>

        <div className="max-h-[520px] overflow-auto bg-surface p-6">
          <div
            className="mx-auto min-h-[600px] w-full max-w-[480px] origin-top bg-white p-10 text-[13px] leading-relaxed text-ink-900 shadow-sm"
            style={{ transform: `scale(${zoom / 100})` }}
          >
            <div className="ml-auto w-fit text-right text-sm">
              <p>Генеральному директору</p>
              <p>ООО «Компания»</p>
              <p>Иванову А.В.</p>
            </div>

            <p className="mt-8 text-center text-sm font-semibold">Служебная записка</p>

            <dl className="mt-6 space-y-1 text-sm">
              <div className="flex gap-2">
                <dt className="w-28 shrink-0 text-ink-600">От:</dt>
                <dd>{senderName}</dd>
              </div>
              <div className="flex gap-2">
                <dt className="w-28 shrink-0 text-ink-600">Должность:</dt>
                <dd>Руководитель отдела</dd>
              </div>
              <div className="flex gap-2">
                <dt className="w-28 shrink-0 text-ink-600">Подразделение:</dt>
                <dd>Отдел развития</dd>
              </div>
              <div className="flex gap-2">
                <dt className="w-28 shrink-0 text-ink-600">Дата:</dt>
                <dd>{requisites.date}</dd>
              </div>
              <div className="flex gap-2">
                <dt className="w-28 shrink-0 text-ink-600">Исх. №:</dt>
                <dd>{requisites.outgoingNumber}</dd>
              </div>
            </dl>

            <p className="mt-6 text-sm font-semibold">{subject}</p>

            <div className="mt-3 space-y-3 text-sm">
              {paragraphs.map((p, i) => (
                <p key={i}>{p}</p>
              ))}
            </div>

            <div className="mt-16 ml-auto w-40 text-right text-sm">
              <div className="border-b border-ink-900/60 pb-6" />
              <p className="mt-1">{requisites.signatureName || senderName}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

import {
  DOCUMENT_TYPES,
  TEMPLATES,
  type DocumentTypeId,
  type TemplateId,
} from "../../types/wizard";
import type { CorporateTemplate, CustomDocType } from "../../lib/scalability";

interface Step2Props {
  documentType: DocumentTypeId;
  template: TemplateId;
  selectedCustomTypeId: string | null;
  selectedCustomTemplateId: string | null;
  extraDocumentTypes: CustomDocType[];
  extraTemplates: CorporateTemplate[];
  onDocumentTypeChange: (id: DocumentTypeId, customId: string | null) => void;
  onTemplateChange: (id: TemplateId, customId: string | null) => void;
  onBack: () => void;
  onNext: () => void;
}

function DocumentTypeIcon({ id }: { id: DocumentTypeId }) {
  if (id === "letter") {
    return (
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
        <rect x="2.5" y="4.5" width="19" height="15" rx="2" stroke="currentColor" strokeWidth="1.4" />
        <path d="M3.5 5.5L12 12.5L20.5 5.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  }
  if (id === "memo") {
    return (
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
        <path d="M7 3H15L18 6V19C18 19.55 17.55 20 17 20H7C6.45 20 6 19.55 6 19V4C6 3.45 6.45 3 7 3Z" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
        <path d="M14 3V6H17" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
        <path d="M8.5 11H15.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
        <path d="M8.5 14H15.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
        <path d="M8.5 17H12" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
      </svg>
    );
  }
  if (id === "report") {
    return (
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
        <path d="M7 3H15L18 6V19C18 19.55 17.55 20 17 20H7C6.45 20 6 19.55 6 19V4C6 3.45 6.45 3 7 3Z" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
        <path d="M14 3V6H17" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
        <path d="M9 11L11 13L15 9" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  }
  // certificate
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
      <rect x="4" y="3" width="16" height="18" rx="2" stroke="currentColor" strokeWidth="1.4" />
      <path d="M8 8H16" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
      <path d="M8 11H16" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
      <path d="M8 14H13" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
      <circle cx="16" cy="17" r="2.5" stroke="currentColor" strokeWidth="1.2" />
      <path d="M14.8 17L15.7 18L17.3 16.2" stroke="currentColor" strokeWidth="1" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function TemplatePreview({ variant, accentColor }: { variant: TemplateId; accentColor?: string }) {
  return (
    <div className="flex gap-2">
      {[0, 1].map((i) => (
        <div key={i} className="flex-1 rounded-lg border border-line bg-surface p-2.5">
          <div
            className={accentColor ? "h-2 w-2/3 rounded-full" : variant === "standard" ? "h-1.5 w-3/4 rounded-full bg-ink-400/40" : "h-2 w-2/3 rounded-full bg-accent-500/50"}
            style={accentColor ? { backgroundColor: accentColor, opacity: 0.6 } : undefined}
          />
          <div className="mt-2 space-y-1">
            {[...Array(4)].map((_, line) => (
              <div key={line} className="h-1 rounded-full bg-ink-400/25" style={{ width: `${90 - line * 12}%` }} />
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

export default function Step2TypeTemplate({
  documentType,
  template,
  selectedCustomTypeId,
  selectedCustomTemplateId,
  extraDocumentTypes,
  extraTemplates,
  onDocumentTypeChange,
  onTemplateChange,
  onBack,
  onNext,
}: Step2Props) {
  return (
    <section className="animate-fade-in">
      <div>
        <h3 className="text-sm font-semibold text-ink-900">1. Выберите тип документа</h3>
        <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {DOCUMENT_TYPES.map((option) => {
            const selected = selectedCustomTypeId === null && option.id === documentType;
            return (
              <button
                key={option.id}
                type="button"
                onClick={() => onDocumentTypeChange(option.id, null)}
                className={[
                  "card-hover group flex flex-col items-center gap-3 rounded-xl border-2 p-4 text-center text-sm transition-all duration-200",
                  selected
                    ? "border-accent-500 bg-accent-50 text-accent-600 shadow-md shadow-accent-500/10"
                    : "border-line bg-white text-ink-600 hover:border-ink-400/50",
                ].join(" ")}
              >
                <div className={[
                  "flex h-12 w-12 items-center justify-center rounded-xl transition-all duration-200",
                  selected ? "bg-accent-100 text-accent-600" : "bg-surface text-ink-400 group-hover:text-ink-600",
                ].join(" ")}>
                  <DocumentTypeIcon id={option.id} />
                </div>
                <span className="font-medium">{option.label}</span>
              </button>
            );
          })}

          {extraDocumentTypes.map((option) => {
            const selected = selectedCustomTypeId === option.id;
            return (
              <button
                key={option.id}
                type="button"
                onClick={() => onDocumentTypeChange(option.baseTypeId, option.id)}
                className={[
                  "card-hover group relative flex flex-col items-center gap-3 rounded-xl border-2 p-4 text-center text-sm transition-all duration-200",
                  selected
                    ? "border-accent-500 bg-accent-50 text-accent-600 shadow-md shadow-accent-500/10"
                    : "border-line bg-white text-ink-600 hover:border-ink-400/50",
                ].join(" ")}
              >
                <span className="absolute right-2 top-2 rounded-full bg-accent-100 px-1.5 py-0.5 text-[10px] font-medium text-accent-600">
                  Своё
                </span>
                <div className={[
                  "flex h-12 w-12 items-center justify-center rounded-xl transition-all duration-200",
                  selected ? "bg-accent-100 text-accent-600" : "bg-surface text-ink-400 group-hover:text-ink-600",
                ].join(" ")}>
                  <DocumentTypeIcon id={option.baseTypeId} />
                </div>
                <span className="font-medium">{option.label}</span>
              </button>
            );
          })}
        </div>
        {extraDocumentTypes.length === 0 && (
          <p className="mt-2 text-xs text-ink-400">
            Свои типы документов можно добавить в настройках — раздел «Расширение типов документов».
          </p>
        )}
      </div>

      <div className="mt-8">
        <h3 className="text-sm font-semibold text-ink-900">2. Выберите шаблон</h3>
        <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-2">
          {TEMPLATES.map((option) => {
            const selected = selectedCustomTemplateId === null && option.id === template;
            return (
              <button
                key={option.id}
                type="button"
                onClick={() => onTemplateChange(option.id, null)}
                className={[
                  "card-hover group rounded-xl border-2 p-5 text-left transition-all duration-200",
                  selected ? "border-accent-500 bg-accent-50 shadow-md shadow-accent-500/10" : "border-line bg-white hover:border-ink-400/50",
                ].join(" ")}
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="text-sm font-semibold text-ink-900">{option.title}</p>
                    <p className="mt-1 text-xs leading-relaxed text-ink-600">{option.description}</p>
                  </div>
                  <span
                    className={[
                      "mt-1 flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2 transition-all duration-200",
                      selected ? "border-accent-600 bg-accent-600" : "border-ink-400/30",
                    ].join(" ")}
                  >
                    {selected && (
                      <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                        <path d="M2 5.2L4 7.2L8 3.2" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                      </svg>
                    )}
                  </span>
                </div>
                <div className="mt-3">
                  <TemplatePreview variant={option.id} />
                </div>
              </button>
            );
          })}

          {extraTemplates.map((option) => {
            const selected = selectedCustomTemplateId === option.id;
            return (
              <button
                key={option.id}
                type="button"
                onClick={() => onTemplateChange(option.baseTemplateId, option.id)}
                className={[
                  "card-hover group rounded-xl border-2 p-5 text-left transition-all duration-200",
                  selected ? "border-accent-500 bg-accent-50 shadow-md shadow-accent-500/10" : "border-line bg-white hover:border-ink-400/50",
                ].join(" ")}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-start gap-2.5">
                    {option.logoDataUrl && (
                      <img src={option.logoDataUrl} alt="" className="h-8 w-8 shrink-0 rounded-md object-contain" />
                    )}
                    <div>
                      <div className="flex items-center gap-2">
                        <p className="text-sm font-semibold text-ink-900">{option.title}</p>
                        <span className="rounded-full bg-accent-100 px-1.5 py-0.5 text-[10px] font-medium text-accent-600">
                          Корпоративный
                        </span>
                      </div>
                      <p className="mt-1 text-xs leading-relaxed text-ink-600">{option.description}</p>
                    </div>
                  </div>
                  <span
                    className={[
                      "mt-1 flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2 transition-all duration-200",
                      selected ? "border-accent-600 bg-accent-600" : "border-ink-400/30",
                    ].join(" ")}
                  >
                    {selected && (
                      <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                        <path d="M2 5.2L4 7.2L8 3.2" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                      </svg>
                    )}
                  </span>
                </div>
                <div className="mt-3">
                  <TemplatePreview variant={option.baseTemplateId} accentColor={option.accentColor} />
                </div>
              </button>
            );
          })}
        </div>
        {extraTemplates.length === 0 && (
          <p className="mt-2 text-xs text-ink-400">
            Корпоративные шаблоны можно добавить в настройках — раздел «Корпоративные шаблоны».
          </p>
        )}
      </div>

      <div className="mt-8 flex items-center justify-between">
        <button
          type="button"
          onClick={onBack}
          className="btn-press flex items-center gap-2 rounded-xl border border-line px-4 py-2.5 text-sm font-medium text-ink-600 transition-all duration-200 hover:bg-surface"
        >
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M9 3L5 7L9 11" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          Назад
        </button>
        <button
          type="button"
          onClick={onNext}
          className="btn-press flex items-center gap-2 rounded-xl bg-accent-600 px-5 py-2.5 text-sm font-medium text-white transition-colors hover:bg-accent-500"
        >
          Далее
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M5 3L9 7L5 11" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
      </div>
    </section>
  );
}

import {
  DOCUMENT_TYPES,
  TEMPLATES,
  type DocumentTypeId,
  type TemplateId,
} from "../../types/wizard";

interface Step2Props {
  documentType: DocumentTypeId;
  template: TemplateId;
  onDocumentTypeChange: (id: DocumentTypeId) => void;
  onTemplateChange: (id: TemplateId) => void;
  onBack: () => void;
  onNext: () => void;
}

function DocumentTypeIcon({ id }: { id: DocumentTypeId }) {
  if (id === "letter") {
    return (
      <svg width="22" height="22" viewBox="0 0 22 22" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
        <rect x="2.5" y="4.5" width="17" height="13" rx="1.6" stroke="currentColor" strokeWidth="1.4" />
        <path d="M3.5 5.5L11 12L18.5 5.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  }
  return (
    <svg width="22" height="22" viewBox="0 0 22 22" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <path d="M6 2.5H14.5L17.5 5.5V18C17.5 18.55 17.05 19 16.5 19H6C5.45 19 5 18.55 5 18V3.5C5 2.95 5.45 2.5 6 2.5Z" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
      <path d="M14 2.5V5.5H17" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
      <path d="M7.5 10H14.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
      <path d="M7.5 12.5H14.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
      <path d="M7.5 15H12" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
    </svg>
  );
}

function TemplatePreview({ variant }: { variant: TemplateId }) {
  return (
    <div className="flex gap-2">
      {[0, 1].map((i) => (
        <div key={i} className="flex-1 rounded-md border border-line bg-surface p-2">
          <div
            className={
              variant === "standard"
                ? "h-1.5 w-3/4 rounded-full bg-ink-400/50"
                : "h-2 w-2/3 rounded-full bg-accent-500/60"
            }
          />
          <div className="mt-2 space-y-1">
            {[...Array(4)].map((_, line) => (
              <div key={line} className="h-1 rounded-full bg-ink-400/30" style={{ width: `${90 - line * 12}%` }} />
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
  onDocumentTypeChange,
  onTemplateChange,
  onBack,
  onNext,
}: Step2Props) {
  return (
    <section>
      <div>
        <h3 className="text-sm font-medium text-ink-900">1. Выберите тип документа</h3>
        <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {DOCUMENT_TYPES.map((option) => {
            const selected = option.id === documentType;
            return (
              <button
                key={option.id}
                type="button"
                onClick={() => onDocumentTypeChange(option.id)}
                className={[
                  "flex flex-col items-center gap-2.5 rounded-xl border p-4 text-center text-sm transition-colors",
                  selected
                    ? "border-accent-500 bg-accent-50 text-accent-600"
                    : "border-line bg-white text-ink-600 hover:border-ink-400/50",
                ].join(" ")}
              >
                <DocumentTypeIcon id={option.id} />
                {option.label}
              </button>
            );
          })}
        </div>
      </div>

      <div className="mt-7">
        <h3 className="text-sm font-medium text-ink-900">2. Выберите шаблон</h3>
        <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-2">
          {TEMPLATES.map((option) => {
            const selected = option.id === template;
            return (
              <button
                key={option.id}
                type="button"
                onClick={() => onTemplateChange(option.id)}
                className={[
                  "rounded-xl border p-4 text-left transition-colors",
                  selected ? "border-accent-500 bg-accent-50" : "border-line bg-white hover:border-ink-400/50",
                ].join(" ")}
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="text-sm font-medium text-ink-900">{option.title}</p>
                    <p className="mt-1 text-xs leading-relaxed text-ink-600">{option.description}</p>
                  </div>
                  <span
                    className={[
                      "mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded-full border-2",
                      selected ? "border-accent-600" : "border-ink-400/40",
                    ].join(" ")}
                  >
                    {selected && <span className="h-2 w-2 rounded-full bg-accent-600" />}
                  </span>
                </div>
                <div className="mt-3">
                  <TemplatePreview variant={option.id} />
                </div>
              </button>
            );
          })}
        </div>
      </div>

      <div className="mt-7 flex items-center justify-between">
        <button
          type="button"
          onClick={onBack}
          className="rounded-lg border border-line px-4 py-2.5 text-sm font-medium text-ink-600 transition-colors hover:bg-surface"
        >
          ← Назад
        </button>
        <button
          type="button"
          onClick={onNext}
          className="rounded-lg bg-accent-600 px-5 py-2.5 text-sm font-medium text-white transition-colors hover:bg-accent-500"
        >
          Далее →
        </button>
      </div>
    </section>
  );
}

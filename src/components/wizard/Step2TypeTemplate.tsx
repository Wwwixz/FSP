import {
  DOCUMENT_TYPES,
  TEMPLATES,
  type DocumentTypeId,
  type TemplateId,
  type TemplateOptions,
} from "../../types/wizard";
import { useState } from "react";

interface Step2Props {
  documentType: DocumentTypeId;
  template: TemplateId;
  templateOptions: TemplateOptions;
  uploadedTemplate: string | null;
  onDocumentTypeChange: (id: DocumentTypeId) => void;
  onTemplateChange: (id: TemplateId) => void;
  onTemplateOptionsChange: (options: TemplateOptions) => void;
  onUploadedTemplateChange: (dataUrl: string | null) => void;
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

function TemplatePreview({ variant }: { variant: TemplateId }) {
  return (
    <div className="flex gap-2">
      {[0, 1].map((i) => (
        <div key={i} className="flex-1 rounded-lg border border-line bg-surface p-2.5">
          <div
            className={
              variant === "standard"
                ? "h-1.5 w-3/4 rounded-full bg-ink-400/40"
                : variant === "custom"
                ? "mx-auto h-1.5 w-3/4 rounded-full border-2 border-dashed border-accent-400 bg-transparent"
                : "h-2 w-2/3 rounded-full bg-accent-500/50"
            }
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

const FONT_CHOICES = ["Times New Roman", "Arial", "Calibri"];
const SIZE_CHOICES = ["12", "13", "14"];
const SPACING_CHOICES = [
  { value: "1", label: "одинарный" },
  { value: "1.15", label: "1,15" },
  { value: "1.5", label: "1,5" },
  { value: "2", label: "двойной" },
];
const INDENT_CHOICES = [
  { value: "0", label: "без отступа" },
  { value: "1", label: "1 см" },
  { value: "1.25", label: "1,25 см" },
  { value: "2", label: "2 см" },
];
const ALIGN_CHOICES = [
  { value: "justify", label: "по ширине" },
  { value: "left", label: "по левому краю" },
  { value: "center", label: "по центру" },
];
const HEADER_ALIGN_CHOICES = [
  { value: "left", label: "по левому краю" },
  { value: "center", label: "по центру" },
  { value: "right", label: "по правому краю" },
];

function CustomTemplateOptionsPanel({
  options,
  onChange,
}: {
  options: TemplateOptions;
  onChange: (options: TemplateOptions) => void;
}) {
  const set = (patch: Partial<TemplateOptions>) => onChange({ ...options, ...patch });
  const selectClass =
    "mt-1.5 w-full rounded-lg border border-line bg-white px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent-500";
  const labelClass = "text-xs font-medium text-ink-600";
  return (
    <div className="mt-4 rounded-xl border border-accent-200 bg-accent-50/50 p-4">
      <p className="text-xs font-semibold text-accent-700">
        Настройки вашего шаблона — применяются при генерации файла
      </p>
      <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-3">
        <div>
          <label className={labelClass}>Шрифт</label>
          <select
            value={options.font}
            onChange={(e) => set({ font: e.target.value })}
            className={selectClass}
          >
            {FONT_CHOICES.map((f) => <option key={f} value={f}>{f}</option>)}
          </select>
        </div>
        <div>
          <label className={labelClass}>Размер, pt</label>
          <select
            value={options.fontSize}
            onChange={(e) => set({ fontSize: e.target.value })}
            className={selectClass}
          >
            {SIZE_CHOICES.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </div>
        <div>
          <label className={labelClass}>Интервал</label>
          <select
            value={options.lineSpacing}
            onChange={(e) => set({ lineSpacing: e.target.value })}
            className={selectClass}
          >
            {SPACING_CHOICES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
        </div>
        <div>
          <label className={labelClass}>Абзацный отступ</label>
          <select
            value={options.indent}
            onChange={(e) => set({ indent: e.target.value })}
            className={selectClass}
          >
            {INDENT_CHOICES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
        </div>
        <div>
          <label className={labelClass}>Выравнивание текста</label>
          <select
            value={options.align}
            onChange={(e) => set({ align: e.target.value as TemplateOptions["align"] })}
            className={selectClass}
          >
            {ALIGN_CHOICES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
        </div>
        <div>
          <label className={labelClass}>Шапка колонтитула</label>
          <select
            value={options.headerAlign}
            onChange={(e) => set({ headerAlign: e.target.value as TemplateOptions["headerAlign"] })}
            className={selectClass}
          >
            {HEADER_ALIGN_CHOICES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
        </div>
        <div className="col-span-2 sm:col-span-3">
          <label className={labelClass}>Текст верхнего колонтитула</label>
          <input
            type="text"
            value={options.header}
            onChange={(e) => set({ header: e.target.value })}
            placeholder="[Название организации]"
            className={selectClass}
          />
        </div>
        <div className="col-span-2 sm:col-span-3">
          <label className={labelClass}>Текст нижнего колонтитула</label>
          <input
            type="text"
            value={options.footer}
            onChange={(e) => set({ footer: e.target.value })}
            placeholder="[Название документа] — [Дата]"
            className={selectClass}
          />
        </div>
      </div>
      <p className="mt-3 text-xs text-ink-500">
        Подсказка: «[Название организации]», «[Название документа]» и «[Дата]» в колонтитулах
        заменяются на реальные значения из реквизитов.
      </p>
    </div>
  );
}

export default function Step2TypeTemplate({
  documentType,
  template,
  templateOptions,
  uploadedTemplate,
  onDocumentTypeChange,
  onTemplateChange,
  onTemplateOptionsChange,
  onUploadedTemplateChange,
  onBack,
  onNext,
}: Step2Props) {
  const [uploadError, setUploadError] = useState<string | null>(null);

  const handleBlankFile = (file: File) => {
    setUploadError(null);
    if (!/\.docx$/i.test(file.name)) {
      setUploadError("Нужен файл .docx — старый .doc или PDF не подойдёт");
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setUploadError("Размер файла не должен превышать 10 МБ");
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      onUploadedTemplateChange(reader.result as string);
      onTemplateChange("uploaded");
    };
    reader.onerror = () => setUploadError("Не удалось прочитать файл");
    reader.readAsDataURL(file);
  };
  return (
    <section className="animate-fade-in">
      <div>
        <h3 className="text-sm font-semibold text-ink-900">1. Выберите тип документа</h3>
        <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {DOCUMENT_TYPES.map((option) => {
            const selected = option.id === documentType;
            return (
              <button
                key={option.id}
                type="button"
                onClick={() => onDocumentTypeChange(option.id)}
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
                <span class="font-medium">{option.label}</span>
              </button>
            );
          })}
        </div>
      </div>

      <div className="mt-8">
        <h3 className="text-sm font-semibold text-ink-900">2. Выберите шаблон</h3>
        <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-2">
          {TEMPLATES.map((option) => {
            const selected = option.id === template;
            return (
              <button
                key={option.id}
                type="button"
                onClick={() => onTemplateChange(option.id)}
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
        </div>

        {template === "custom" && (
          <CustomTemplateOptionsPanel options={templateOptions} onChange={onTemplateOptionsChange} />
        )}

        {template === "uploaded" && (
          <div className="mt-4 rounded-xl border border-accent-200 bg-accent-50/50 p-4">
            <p className="text-xs font-semibold text-accent-700">
              Фирменный бланк — документ будет оформлен на его основе
            </p>
            <div
              className="mt-3 rounded-xl border border-dashed border-line bg-surface/60 p-5 text-center"
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => {
                e.preventDefault();
                const f = e.dataTransfer.files?.[0];
                if (f) handleBlankFile(f);
              }}
            >
              <label className="flex cursor-pointer flex-col items-center gap-1.5">
                {uploadedTemplate ? (
                  <span className="text-sm font-medium text-success-600">
                    ✅ Бланк загружен — нажмите, чтобы заменить
                  </span>
                ) : (
                  <>
                    <span className="text-sm font-medium text-ink-900">
                      Загрузите бланк .docx или перетащите сюда
                    </span>
                    <span className="text-xs text-ink-500">
                      Сохранятся колонтитулы, поля и шрифты вашего бланка
                    </span>
                  </>
                )}
                <input
                  type="file"
                  accept=".docx"
                  className="sr-only"
                  onChange={(e) => {
                    const f = e.target.files?.[0];
                    if (f) handleBlankFile(f);
                    e.target.value = "";
                  }}
                />
              </label>
              {uploadedTemplate && (
                <button
                  type="button"
                  onClick={() => {
                    onUploadedTemplateChange(null);
                    onTemplateChange("standard");
                  }}
                  className="mt-3 rounded-lg border border-line px-3 py-1.5 text-xs font-medium text-ink-600 transition-colors hover:bg-surface"
                >
                  Убрать бланк
                </button>
              )}
              {uploadError && <p className="mt-2 text-xs text-danger-500">⚠ {uploadError}</p>}
            </div>
          </div>
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

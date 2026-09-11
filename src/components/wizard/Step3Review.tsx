import { useMemo, useState } from "react";
import type { RequisiteCheck } from "../../types/wizard";

interface Step3Props {
  improvedText: string;
  onImprovedTextChange: (text: string) => void;
  requisites: RequisiteCheck[];
  onBack: () => void;
  onNext: () => void;
}

function countWords(text: string): number {
  const trimmed = text.trim();
  return trimmed.length === 0 ? 0 : trimmed.split(/\s+/).length;
}

export default function Step3Review({
  improvedText,
  onImprovedTextChange,
  requisites,
  onBack,
  onNext,
}: Step3Props) {
  const [isEditing, setIsEditing] = useState(false);
  const wordCount = useMemo(() => countWords(improvedText), [improvedText]);
  const missingRequisites = requisites.filter((r) => r.status === "missing");

  return (
    <section>
      <h2 className="text-lg font-medium text-ink-900">
        Проверьте и при необходимости отредактируйте текст
      </h2>

      <div className="mt-4 flex items-start gap-2.5 rounded-xl bg-success-50 p-4 text-sm text-ink-900">
        <CheckIcon className="mt-0.5 shrink-0 text-success-500" />
        <p>
          Текст был проверен: исправлены ошибки, приведён к деловому стилю,
          структурирован.
        </p>
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-[1.4fr_1fr]">
        <div className="rounded-xl border border-line bg-white p-4">
          <div className="flex items-center justify-between">
            <p className="text-sm font-medium text-ink-900">Улучшенный текст</p>
            <button
              type="button"
              onClick={() => setIsEditing((v) => !v)}
              className="flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium text-accent-600 hover:bg-accent-50"
            >
              <EditIcon />
              {isEditing ? "Готово" : "Редактировать"}
            </button>
          </div>

          {isEditing ? (
            <textarea
              value={improvedText}
              onChange={(e) => onImprovedTextChange(e.target.value)}
              rows={7}
              className="mt-3 w-full resize-none rounded-lg border border-line p-3 text-sm text-ink-900 focus:border-accent-500"
            />
          ) : (
            <div className="mt-3 whitespace-pre-line text-sm leading-relaxed text-ink-900">
              {improvedText}
            </div>
          )}

          <p className="mt-3 text-xs text-ink-400">{wordCount} слов</p>
        </div>

        <div className="rounded-xl border border-line bg-white p-4">
          <p className="text-sm font-medium text-ink-900">Проверка реквизитов</p>
          <ul className="mt-3 space-y-3">
            {requisites.map((item) => (
              <li key={item.label} className="flex items-start justify-between gap-2 text-sm">
                <span className="text-ink-900">{item.label}</span>
                {item.status === "done" ? (
                  <CheckIcon className="mt-0.5 shrink-0 text-success-500" />
                ) : (
                  <span className="flex flex-col items-end">
                    <span className="flex items-center gap-1 text-danger-500">
                      <CrossIcon />
                    </span>
                    {item.hint && <span className="text-xs text-danger-500">{item.hint}</span>}
                  </span>
                )}
              </li>
            ))}
          </ul>

          {missingRequisites.length > 0 && (
            <p className="mt-4 rounded-lg bg-accent-50 p-3 text-xs leading-relaxed text-accent-600">
              Пожалуйста, укажите недостающие реквизиты или мы добавим их позже.
            </p>
          )}
        </div>
      </div>

      <div className="mt-6 flex items-center justify-between">
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

function CheckIcon({ className = "" }: { className?: string }) {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" className={className} aria-hidden="true">
      <circle cx="8" cy="8" r="7" fill="currentColor" fillOpacity="0.15" />
      <path d="M5 8.2L7.1 10.3L11.2 5.9" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function CrossIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <circle cx="8" cy="8" r="7" fill="currentColor" fillOpacity="0.15" />
      <path d="M5.5 5.5L10.5 10.5M10.5 5.5L5.5 10.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  );
}

function EditIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <path d="M10.5 2.5L13.5 5.5L5.5 13.5H2.5V10.5L10.5 2.5Z" stroke="currentColor" strokeWidth="1.3" strokeLinejoin="round" />
    </svg>
  );
}

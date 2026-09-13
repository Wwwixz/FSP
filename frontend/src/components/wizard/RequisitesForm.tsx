import { useState } from "react";
import type { RequisiteCheckDto } from "../../types/wizard";

interface RequisitesFormProps {
  checks: RequisiteCheckDto[];
  values: Record<string, string>;
  onChange: (values: Record<string, string>) => void;
  onBack: () => void;
  onNext: () => void;
}

interface FieldError {
  key: string;
  message: string;
}

function isValidDateFormat(value: string): boolean {
  if (!value || !value.trim()) return true;
  const trimmed = value.trim();
  const regex = /^\d{2}\.\d{2}\.\d{4}$/;
  if (!regex.test(trimmed)) return false;
  const [day, month, year] = trimmed.split(".").map(Number);
  if (month < 1 || month > 12) return false;
  if (day < 1 || day > 31) return false;
  if ([4, 6, 9, 11].includes(month) && day > 30) return false;
  if (month === 2) {
    const isLeap = (year % 4 === 0 && year % 100 !== 0) || (year % 400 === 0);
    return day <= (isLeap ? 29 : 28);
  }
  return true;
}

function isValidNumberFormat(value: string): boolean {
  if (!value || !value.trim()) return true;
  const trimmed = value.trim();
  if (!/\d/.test(trimmed)) return false;
  const regex = /^[\dА-Яа-яA-Za-z\-\/\\]+$/;
  return regex.test(trimmed);
}

function CalendarIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <rect x="2.5" y="3.2" width="11" height="10.3" rx="1.4" stroke="currentColor" strokeWidth="1.3" />
      <path d="M2.5 6.2H13.5" stroke="currentColor" strokeWidth="1.3" />
      <path d="M5.3 1.8V4.2M10.7 1.8V4.2" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
    </svg>
  );
}

const KEY_PLACEHOLDERS: Record<string, string> = {
  recipient: "Кому, например: Генеральному директору ООО «Ромашка» Иванову И.И.",
  author: "От кого, например: начальник отдела Петров П.П.",
  subject: "Короткий заголовок или тема",
  date: "ДД.ММ.ГГГГ, например 12.03.2025",
  number: "Например: 47-СЗ, 12/23",
  signature: "ФИО, например: Петров П.П.",
  salutation: "Обращение, например: Уважаемый Фёдор Фёдорович!",
  executor: "ФИО + контакты исполнителя",
  organization: "Наименование организации",
};

export default function RequisitesForm({
  checks,
  values,
  onChange,
  onBack,
  onNext,
}: RequisitesFormProps) {
  const missingChecks = checks.filter((c) => c.status === "missing");
  const allMissingFilled = missingChecks.every((c) => (values[c.key] ?? "").trim().length > 0);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const validateField = (key: string, value: string): string | null => {
    if (key === "date" && !isValidDateFormat(value)) {
      return "Введите дату в формате ДД.ММ.ГГГГ (например 12.03.2025)";
    }
    if (key === "number" && !isValidNumberFormat(value)) {
      return "Номер должен содержать цифры. Допустимы: буквы, дефис, слэш (например 47-СЗ, 12/23)";
    }
    return null;
  };

  const setValue = (key: string, v: string) => {
    const nextValues = { ...values, [key]: v };
    onChange(nextValues);
    const err = validateField(key, v);
    setErrors((prev) => {
      const next = { ...prev };
      if (err) next[key] = err;
      else delete next[key];
      return next;
    });
  };

  const hasFormatErrors = Object.keys(errors).length > 0;

  return (
    <section>
      <h2 className="text-lg font-medium text-ink-900">Заполните недостающие реквизиты</h2>
      <p className="mt-1 text-sm text-ink-600">
        Если не хотите заполнять сейчас — оставьте поле пустым, в документе
        появится пометка «[Заполнить]».
      </p>

      <div className="mt-5 space-y-5">
        {missingChecks.length === 0 && (
          <p className="rounded-lg bg-success-50 p-4 text-sm text-ink-900">
            Все обязательные реквизиты заполнены ✅
          </p>
        )}

        {missingChecks.map((check) => {
          const value = values[check.key] ?? "";
          const missing = value.trim().length === 0;
          const isDate = check.key === "date";
          const isNumber = check.key === "number";
          const fieldError = errors[check.key];
          const showErrorBorder = !missing && fieldError;
          return (
            <div key={check.key}>
              <label htmlFor={`req-${check.key}`} className="text-sm text-ink-900">
                {check.label} <span className="text-danger-500">*</span>
              </label>
              {check.hint && (
                <p className="mt-0.5 text-xs text-ink-500">{check.hint}</p>
              )}
              <div className="relative mt-2">
                <input
                  id={`req-${check.key}`}
                  type="text"
                  value={value}
                  onChange={(e) => setValue(check.key, e.target.value)}
                  placeholder={KEY_PLACEHOLDERS[check.key] ?? ""}
                  inputMode={isDate || isNumber ? "text" : "text"}
                  className={[
                    "w-full rounded-lg border bg-white px-3.5 py-2.5 text-sm text-ink-900 placeholder:text-ink-400 outline-none",
                    showErrorBorder
                      ? "border-danger-500 bg-danger-50/40 focus:border-danger-500"
                      : missing
                      ? "border-danger-500 bg-danger-50/40 focus:border-danger-500"
                      : "border-line focus:border-accent-500",
                  ].join(" ")}
                />
                {isDate && (
                  <span className="pointer-events-none absolute right-3.5 top-1/2 -translate-y-1/2 text-ink-400">
                    <CalendarIcon />
                  </span>
                )}
              </div>
              {fieldError && (
                <p className="mt-1.5 text-xs text-danger-500">⚠️ {fieldError}</p>
              )}
              {!fieldError && missing && (
                <p className="mt-1.5 text-xs text-danger-500">
                  Пока пусто — будет поставлена пометка «[Заполнить: {check.label}]»
                </p>
              )}
            </div>
          );
        })}
      </div>

      <div className="mt-8 flex items-center justify-between">
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
          disabled={hasFormatErrors}
          title={hasFormatErrors ? "Сначала исправьте ошибки в полях" : ""}
          className={[
            "rounded-lg px-5 py-2.5 text-sm font-medium text-white transition-colors",
            hasFormatErrors
              ? "bg-ink-300 cursor-not-allowed"
              : "bg-accent-600 hover:bg-accent-500",
          ].join(" ")}
        >
          {allMissingFilled ? "Далее →" : "Пропустить (оставить пометки) →"}
        </button>
      </div>
    </section>
  );
}

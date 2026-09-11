import type { RequisiteValues } from "../../types/wizard";

interface RequisitesFormProps {
  values: RequisiteValues;
  onChange: (values: RequisiteValues) => void;
  onBack: () => void;
  onNext: () => void;
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

export default function RequisitesForm({ values, onChange, onBack, onNext }: RequisitesFormProps) {
  const numberMissing = values.outgoingNumber.trim().length === 0;
  const signatureMissing = values.signatureName.trim().length === 0;
  const canProceed = !numberMissing && !signatureMissing && values.date.trim().length > 0;

  return (
    <section>
      <h2 className="text-lg font-medium text-ink-900">Заполните недостающие реквизиты</h2>

      <div className="mt-5 space-y-5">
        <div>
          <label htmlFor="outgoing-number" className="text-sm text-ink-900">
            Исходящий номер <span className="text-danger-500">*</span>
          </label>
          <input
            id="outgoing-number"
            type="text"
            value={values.outgoingNumber}
            onChange={(e) => onChange({ ...values, outgoingNumber: e.target.value })}
            placeholder="Например: 123/23"
            className={[
              "mt-2 w-full rounded-lg border bg-white px-3.5 py-2.5 text-sm text-ink-900 placeholder:text-ink-400",
              numberMissing ? "border-danger-500 bg-danger-50/40" : "border-line focus:border-accent-500",
            ].join(" ")}
          />
          {numberMissing && <p className="mt-1.5 text-xs text-danger-500">Обязательное поле</p>}
        </div>

        <div>
          <label htmlFor="doc-date" className="text-sm text-ink-900">
            Дата <span className="text-danger-500">*</span>
          </label>
          <div className="relative mt-2">
            <input
              id="doc-date"
              type="text"
              value={values.date}
              onChange={(e) => onChange({ ...values, date: e.target.value })}
              className="w-full rounded-lg border border-line bg-white px-3.5 py-2.5 text-sm text-ink-900 focus:border-accent-500"
            />
            <span className="pointer-events-none absolute right-3.5 top-1/2 -translate-y-1/2 text-ink-400">
              <CalendarIcon />
            </span>
          </div>
        </div>

        <div>
          <label htmlFor="signature-name" className="text-sm text-ink-900">
            Подпись
          </label>
          <input
            id="signature-name"
            type="text"
            value={values.signatureName}
            onChange={(e) => onChange({ ...values, signatureName: e.target.value })}
            placeholder="ФИО"
            className={[
              "mt-2 w-full rounded-lg border bg-white px-3.5 py-2.5 text-sm text-ink-900 placeholder:text-ink-400",
              signatureMissing ? "border-danger-500 bg-danger-50/40" : "border-line focus:border-accent-500",
            ].join(" ")}
          />
          {signatureMissing && <p className="mt-1.5 text-xs text-danger-500">Укажите ФИО</p>}
        </div>
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
          onClick={() => canProceed && onNext()}
          className="rounded-lg bg-accent-600 px-5 py-2.5 text-sm font-medium text-white transition-colors hover:bg-accent-500"
        >
          Далее →
        </button>
      </div>
    </section>
  );
}

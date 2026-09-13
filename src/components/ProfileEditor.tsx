import { useMemo, useState } from "react";
import {
  loadProfile,
  profileAuthorLine,
  profileExecutorLine,
  saveProfile,
  shortName,
  validateProfile,
  type UserProfile,
} from "../lib/profile";

type ProfileField = keyof UserProfile;

const FIELD_DEFS: Array<{
  key: ProfileField;
  label: string;
  placeholder: string;
  required?: boolean;
  hint?: string;
}> = [
  { key: "fullName", label: "ФИО", placeholder: "Петров Иван Сергеевич", required: true, hint: "Подставляется в «От кого» и подпись" },
  { key: "position", label: "Должность", placeholder: "специалист отдела кадров" },
  { key: "department", label: "Подразделение", placeholder: "отдел кадров" },
  { key: "organization", label: "Организация", placeholder: "ООО «Ромашка»", required: true, hint: "Попадёт в колонтитул и в генератор печати" },
  { key: "city", label: "Город", placeholder: "г. Красноярск", hint: "Используется при генерации печати" },
  { key: "phone", label: "Телефон", placeholder: "+7 999 123-45-67", hint: "Подставляется в реквизит «Исполнитель»" },
  { key: "email", label: "E-mail", placeholder: "petrov@example.ru" },
];

/**
 * Редактор профиля на странице «Настройки».
 * Данные обязательны к заполнению (не «из болды») и автоматически
 * подставляются в реквизиты всех новых документов, включая демо-примеры.
 */
export default function ProfileEditor() {
  const [profile, setProfile] = useState<UserProfile>(() => loadProfile());
  const [errors, setErrors] = useState<Partial<Record<ProfileField, string>>>({});
  const [status, setStatus] = useState<string | null>(null);

  const filled = useMemo(
    () => profile.fullName.trim() && profile.organization.trim(),
    [profile.fullName, profile.organization],
  );

  const setValue = (key: ProfileField, value: string) => {
    setProfile((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => {
      if (!prev[key]) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });
  };

  const handleSave = () => {
    const found = validateProfile(profile);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      setStatus("❌ Исправьте ошибки — в документ должны попадать настоящие данные");
      return;
    }
    saveProfile(profile);
    setStatus("✅ Профиль сохранён — данные будут подставляться в новые документы");
    setTimeout(() => setStatus(null), 4000);
  };

  const inputClass = (key: ProfileField) => [
    "mt-2 w-full rounded-lg border bg-white px-3.5 py-2.5 text-sm text-ink-900 placeholder:text-ink-400 outline-none transition-colors",
    errors[key]
      ? "border-danger-500 bg-danger-50/40 focus:border-danger-500"
      : "border-line focus:border-accent-500",
  ].join(" ");

  return (
    <div>
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-xl font-semibold text-ink-900">Настройки</h1>
          <p className="mt-1 text-sm text-ink-600">
            Эти данные будут автоматически подставляться в реквизиты новых документов —
            в том числе при использовании демо-примеров.
          </p>
        </div>
        <span
          className={[
            "shrink-0 rounded-full px-3 py-1 text-xs font-medium",
            filled ? "bg-success-50 text-success-600" : "bg-warning-50 text-ink-600",
          ].join(" ")}
        >
          {filled ? "Профиль заполнен" : "Заполните обязательные поля"}
        </span>
      </div>

      <div className="mt-6 grid grid-cols-1 gap-5 sm:grid-cols-2">
        {FIELD_DEFS.map((field) => (
          <div key={field.key} className={field.key === "email" ? "sm:col-span-2" : ""}>
            <label htmlFor={`profile-${field.key}`} className="text-sm text-ink-900">
              {field.label}
              {field.required && <span className="text-danger-500"> *</span>}
            </label>
            <input
              id={`profile-${field.key}`}
              type="text"
              value={profile[field.key]}
              onChange={(e) => setValue(field.key, e.target.value)}
              placeholder={field.placeholder}
              className={inputClass(field.key)}
            />
            {errors[field.key] ? (
              <p className="mt-1.5 text-xs text-danger-500">⚠ {errors[field.key]}</p>
            ) : (
              field.hint && <p className="mt-1.5 text-xs text-ink-500">{field.hint}</p>
            )}
          </div>
        ))}
      </div>

      <div className="mt-6 rounded-xl bg-surface p-4 text-xs leading-relaxed text-ink-600">
        <span className="font-medium text-ink-900">Как данные попадут в документ:</span>{" "}
        От кого — «{profileAuthorLine(profile) || "—"}», подпись — «
        {profile.fullName.trim() ? shortName(profile.fullName) : "—"}», исполнитель — «
        {profileExecutorLine(profile) || "—"}».
      </div>

      <div className="mt-5 flex items-center justify-between gap-4 flex-wrap">
        {status && <p className="text-sm text-ink-900">{status}</p>}
        <button
          type="button"
          onClick={handleSave}
          className="ml-auto rounded-lg bg-accent-600 px-5 py-2.5 text-sm font-medium text-white transition-colors hover:bg-accent-500"
        >
          Сохранить профиль
        </button>
      </div>
    </div>
  );
}

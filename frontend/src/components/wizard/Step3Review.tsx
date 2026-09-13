import { useEffect, useMemo, useRef, useState } from "react";
import type { RequisiteCheck } from "../../types/wizard";
import SignaturePad, { SIGNATURE_STORAGE_KEY } from "./SignaturePad";

export const PHOTO_STORAGE_KEY = "dochelper:photo-image";

function PhotoUploadPad({ compact = false }: { compact?: boolean }) {
  const [currentPhoto, setCurrentPhoto] = useState<string | null>(() => {
    if (typeof window === "undefined") return null;
    try {
      const v = window.localStorage.getItem(PHOTO_STORAGE_KEY);
      return v && v.trim() ? v : null;
    } catch {
      return null;
    }
  });
  const [msg, setMsg] = useState<{ text: string; err?: boolean } | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (msg) {
      const t = setTimeout(() => setMsg(null), 3000);
      return () => clearTimeout(t);
    }
  }, [msg]);

  const setLocal = (b64: string | null) => {
    try {
      if (b64) window.localStorage.setItem(PHOTO_STORAGE_KEY, b64);
      else window.localStorage.removeItem(PHOTO_STORAGE_KEY);
    } catch { /* ignore */ }
    setCurrentPhoto(b64);
  };

  const processFile = (file: File) => {
    if (!file.type.startsWith("image/")) {
      setMsg({ text: "Можно загружать только изображения", err: true });
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setMsg({ text: "Размер файла не должен превышать 5 МБ", err: true });
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const v = reader.result as string;
      setLocal(v);
      setMsg({ text: "✅ Фото сохранено" });
    };
    reader.onerror = () => setMsg({ text: "Ошибка чтения файла", err: true });
    reader.readAsDataURL(file);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    const f = e.dataTransfer.files?.[0];
    if (f) processFile(f);
  };

  const deletePhoto = () => {
    setLocal(null);
    setMsg({ text: "Фото удалено" });
  };

  return (
    <div
      className={[
        "rounded-2xl border border-line bg-card shadow-sm shadow-ink-900/[0.03]",
        compact ? "p-4" : "p-5",
      ].join(" ")}
    >
      <div className="flex items-start justify-between gap-3 mb-3">
        <div>
          <h3 className="text-sm font-semibold text-ink-900">Фото 📷</h3>
          <p className="mt-0.5 text-xs text-ink-500">
            Загрузите своё фото (аватар) для шапки документа
          </p>
        </div>
      </div>

      {/* Current preview */}
      <div className="mb-4">
        {currentPhoto ? (
          <div className="flex items-start justify-between gap-3 rounded-lg border border-line bg-surface/60 p-3">
            <div className="min-w-0">
              <p className="text-xs font-medium text-ink-700 mb-2">Текущее фото</p>
              <div className="rounded-md border border-line bg-white p-2 inline-block">
                <img
                  src={currentPhoto}
                  alt="Фото"
                  className="h-20 w-16 object-cover rounded"
                />
              </div>
            </div>
            <button
              type="button"
              onClick={deletePhoto}
              className="shrink-0 rounded-md border border-line px-2.5 py-1.5 text-xs font-medium text-ink-600 hover:bg-surface transition-colors"
            >
              🗑 Удалить
            </button>
          </div>
        ) : (
          <div className="rounded-lg border border-dashed border-line bg-surface/40 p-3 text-xs text-ink-500">
            ⚠️ Фото не задано — в документе будет только текст
          </div>
        )}
      </div>

      <div
        onDragOver={(e) => e.preventDefault()}
        onDrop={handleDrop}
        className="rounded-lg border border-dashed border-line bg-surface/60 p-4 flex flex-col items-center gap-2"
      >
        <label
          htmlFor="photo-pad-file-input"
          className="flex cursor-pointer flex-col items-center gap-2 text-center hover:bg-white/60 rounded-lg px-4 py-3 w-full transition-colors"
        >
          <svg width="36" height="36" viewBox="0 0 24 24" fill="none" className="text-accent-500">
            <rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" strokeWidth="1.8" />
            <circle cx="9" cy="11" r="2" stroke="currentColor" strokeWidth="1.8" />
            <path d="M21 17L16 12L5 21" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          <p className="text-sm font-medium text-ink-900">Нажмите или перетащите фото</p>
          <p className="text-xs text-ink-500">PNG, JPG — до 5 МБ</p>
        </label>
        <input
          id="photo-pad-file-input"
          ref={fileInputRef}
          type="file"
          accept="image/png,image/jpeg,image/jpg"
          className="sr-only"
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) processFile(f);
            e.target.value = "";
          }}
        />
      </div>

      {msg && (
        <p className={["mt-3 text-xs min-h-[1rem]", msg.err ? "text-danger-600" : "text-success-600"].join(" ")}>
          {msg.text}
        </p>
      )}
    </div>
  );
}

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
    <section className="animate-fade-in">
      <h2 className="text-lg font-medium text-ink-900">
        Проверьте и при необходимости отредактируйте текст
      </h2>

      <div className="mt-4 flex items-start gap-3 rounded-xl bg-success-50 p-4 text-sm text-ink-900">
        <div className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-success-500 text-white">
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
            <path d="M2.5 6.2L4.8 8.5L9.5 3.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </div>
        <p>
          Текст обработан: исправлены ошибки, приведён к деловому стилю.
          Проверьте результат и при необходимости отредактируйте.
        </p>
      </div>

      <div className="mt-5 grid grid-cols-1 gap-4 lg:grid-cols-[1.4fr_1fr]">
        {/* Text editor */}
        <div className="rounded-xl border border-line bg-white p-4">
          <div className="flex items-center justify-between">
            <p className="text-sm font-semibold text-ink-900">Улучшенный текст</p>
            <button
              type="button"
              onClick={() => setIsEditing((v) => !v)}
              className="flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-medium text-accent-600 transition-colors hover:bg-accent-50"
            >
              {isEditing ? (
                <>
                  <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                    <path d="M2 6.2L4.8 9L10 3.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                  Готово
                </>
              ) : (
                <>
                  <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                    <path d="M8 2L10.5 4.5L4.5 10.5H2V8L8 2Z" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round" />
                  </svg>
                  Редактировать
                </>
              )}
            </button>
          </div>

          {isEditing ? (
            <textarea
              value={improvedText}
              onChange={(e) => onImprovedTextChange(e.target.value)}
              rows={8}
              className="mt-3 w-full resize-none rounded-lg border border-line p-3 text-sm leading-relaxed text-ink-900 transition-all duration-200 focus:border-accent-500"
            />
          ) : (
            <div className="mt-3 whitespace-pre-line text-sm leading-relaxed text-ink-900">
              {improvedText}
            </div>
          )}

          <div className="mt-3 flex items-center gap-3 text-xs text-ink-400">
            <span className="flex items-center gap-1">
              <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                <path d="M2 3H10M2 6H8M2 9H6" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
              </svg>
              {wordCount} слов
            </span>
            <span>{improvedText.length.toLocaleString()} символов</span>
          </div>
        </div>

        {/* Requisites check */}
        <div className="rounded-xl border border-line bg-white p-4">
          <p className="text-sm font-semibold text-ink-900">Проверка реквизитов</p>
          <ul className="mt-3 space-y-3">
            {requisites.map((item) => (
              <li key={item.label} className="flex items-start justify-between gap-2 text-sm">
                <span className="text-ink-900">{item.label}</span>
                {item.status === "done" ? (
                  <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-success-50 text-success-500">
                    <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                      <path d="M2 5.2L4 7.2L8 3.2" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                  </span>
                ) : (
                  <span className="flex flex-col items-end">
                    <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-danger-50 text-danger-500">
                      <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                        <path d="M3 3L7 7M7 3L3 7" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
                      </svg>
                    </span>
                    {item.hint && <span className="mt-1 text-xs text-danger-500">{item.hint}</span>}
                  </span>
                )}
              </li>
            ))}
          </ul>

          {missingRequisites.length > 0 && (
            <div className="mt-4 rounded-lg bg-warning-50 p-3 text-xs leading-relaxed text-ink-600">
              <span className="font-medium text-ink-900">Внимание:</span>{" "}
              {missingRequisites.length === 1
                ? `Отсутствует ${missingRequisites.length} реквизит`
                : `Отсутствует ${missingRequisites.length} реквизитов`}{" "}
              — мы запросим их у вас.
            </div>
          )}
        </div>
      </div>

      <div className="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <SignaturePad compact />
        <PhotoUploadPad compact />
      </div>

      <div className="mt-6 flex items-center justify-between">
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

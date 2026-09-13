import { useEffect, useMemo, useRef, useState } from "react";
import type { RequisiteCheck } from "../../types/wizard";
import SignaturePad, { SIGNATURE_STORAGE_KEY } from "./SignaturePad";
import StampPad from "./StampPad";
import { diffWords } from "../../lib/diff";

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
  rawText: string;
  improvedText: string;
  onImprovedTextChange: (text: string) => void;
  requisites: RequisiteCheck[];
  documentId?: string;
  warnings?: string[];
  onBack: () => void;
  onNext: () => void;
}

function countWords(text: string): number {
  const trimmed = text.trim();
  return trimmed.length === 0 ? 0 : trimmed.split(/\s+/).length;
}

/**
 * Панель «Что исправил ИИ»: пословное сравнение черновика и улучшенного
 * текста. Красное зачёркнутое — убрано из черновика, зелёное — добавлено
 * при обработке. Наглядно показывает эксперту сценарий 2 ТЗ: ошибки
 * исправлены, смысл и факты не тронуты.
 */
function AiChangesPanel({ rawText, improvedText }: { rawText: string; improvedText: string }) {
  const diff = useMemo(() => diffWords(rawText, improvedText), [rawText, improvedText]);
  const totalChanges = diff.added + diff.removed;
  if (totalChanges === 0) {
    return (
      <div className="mt-3 rounded-lg bg-success-50 p-3 text-xs text-ink-600">
        ИИ не нашёл, что исправить — черновик уже был грамотным ✅
      </div>
    );
  }
  return (
    <div className="mt-3 rounded-lg border border-accent-100 bg-accent-50/40 p-3">
      <div className="flex flex-wrap items-center gap-2 text-xs">
        <span className="rounded-full bg-white px-2.5 py-1 font-semibold text-accent-700 shadow-sm">
          Исправлений: {totalChanges}
        </span>
        <span className="rounded-full bg-success-50 px-2.5 py-1 text-success-700">
          + добавлено {diff.added}
        </span>
        <span className="rounded-full bg-danger-50 px-2.5 py-1 text-danger-600">
          − убрано {diff.removed}
        </span>
      </div>
      <p className="mt-3 max-h-72 overflow-auto whitespace-pre-wrap break-words text-sm leading-relaxed text-ink-900">
        {diff.tokens.map((token, idx) => {
          if (token.type === "same") return <span key={idx}>{token.text}</span>;
          if (token.type === "add") {
            return (
              <span key={idx} className="rounded bg-success-100 px-0.5 text-success-700">
                {token.text}
              </span>
            );
          }
          return (
            <span
              key={idx}
              className="rounded bg-danger-50 px-0.5 text-danger-500 line-through decoration-danger-400"
              title="Убрано ИИ при обработке"
            >
              {token.text}
            </span>
          );
        })}
      </p>
    </div>
  );
}

export default function Step3Review({
  rawText,
  improvedText,
  onImprovedTextChange,
  requisites,
  documentId,
  warnings = [],
  onBack,
  onNext,
}: Step3Props) {
  const [isEditing, setIsEditing] = useState(false);
  const [showDiff, setShowDiff] = useState(false);
  const [refining, setRefining] = useState<string | null>(null);
  const [refineMsg, setRefineMsg] = useState<{ text: string; err?: boolean } | null>(null);
  const [customInstruction, setCustomInstruction] = useState("");
  const wordCount = useMemo(() => countWords(improvedText), [improvedText]);
  const missingRequisites = requisites.filter((r) => r.status === "missing");

  /**
   * Доработка текста командой ИИ: сервер применяет инструкцию к улучшенному
   * тексту и возвращает новый вариант. Факты сохраняются, ничего не выдумывается.
   */
  const sendRefine = async (instruction: string) => {
    if (!documentId || !instruction.trim() || refining) return;
    setRefining(instruction);
    setRefineMsg(null);
    try {
      const res = await fetch(`/api/documents/${documentId}/refine`, {
        method: "POST",
        headers: { "Content-Type": "application/json; charset=utf-8" },
        body: JSON.stringify({ instruction: instruction.trim() }),
      });
      const json = await res.json();
      if (!res.ok || !json?.success) {
        setRefineMsg({ text: json?.error?.message ?? "ИИ-доработка недоступна — текст не изменён", err: true });
        return;
      }
      onImprovedTextChange(String(json.improvedText ?? improvedText));
      setRefineMsg({
        text: json.warning ?? `Готово — ИИ применил: «${instruction.trim()}»`,
        err: Boolean(json.warning),
      });
    } catch {
      setRefineMsg({ text: "Сервис ИИ недоступен — текст не изменён", err: true });
    } finally {
      setRefining(null);
    }
  };

  const REFINE_CHIPS = [
    { label: "Короче", instruction: "Сделай текст короче, убери повторы, сохрани все факты и реквизиты" },
    { label: "Официальнее", instruction: "Сделай формулировки более официальными и канцелярски точными" },
    { label: "Вежливее", instruction: "Сделай тон более вежливым и уважительным, не меняя сути" },
    { label: "Подробнее", instruction: "Добавь уместные пояснения к просьбам, не придумывая новых фактов" },
  ];

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

      {warnings.length > 0 && (
        <div className="mt-3 rounded-xl border border-warning-100 bg-warning-50 p-4 text-sm text-ink-900">
          {warnings.map((w, i) => (
            <p key={i} className="flex items-start gap-2">
              <span aria-hidden="true">⚠</span>
              <span>{w}</span>
            </p>
          ))}
        </div>
      )}

      <div className="mt-5 grid grid-cols-1 gap-4 lg:grid-cols-[1.4fr_1fr]">
        {/* Text editor */}
        <div className="rounded-xl border border-line bg-white p-4">
          <div className="flex items-center justify-between">
            <p className="text-sm font-semibold text-ink-900">Улучшенный текст</p>
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => setShowDiff((v) => !v)}
                className={[
                  "flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-medium transition-colors",
                  showDiff ? "bg-accent-100 text-accent-700" : "text-accent-600 hover:bg-accent-50",
                ].join(" ")}
                title="Пословное сравнение с исходным черновиком"
              >
                <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                  <path d="M6 1.5V10.5M6 1.5L3.5 4M6 1.5L8.5 4M6 10.5L3.5 8M6 10.5L8.5 8" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
                Что исправил ИИ
              </button>
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
          </div>

          {showDiff && rawText.trim() && (
            <AiChangesPanel rawText={rawText} improvedText={improvedText} />
          )}

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

          {documentId && (
            <div className="mt-4 rounded-xl border border-accent-100 bg-accent-50/40 p-3">
              <p className="text-xs font-semibold text-accent-700">
                ✨ Доработать с ИИ — он изменит только текст, факты останутся
              </p>
              <div className="mt-2 flex flex-wrap gap-2">
                {REFINE_CHIPS.map((chip) => (
                  <button
                    key={chip.label}
                    type="button"
                    onClick={() => sendRefine(chip.instruction)}
                    disabled={Boolean(refining)}
                    className={[
                      "rounded-full px-3 py-1.5 text-xs font-medium transition-colors",
                      refining === chip.instruction
                        ? "bg-accent-600 text-white"
                        : "bg-white text-ink-900 border border-line hover:border-accent-300 hover:bg-accent-50",
                    ].join(" ")}
                  >
                    {refining === chip.instruction ? "⏳ …" : chip.label}
                  </button>
                ))}
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <input
                  type="text"
                  value={customInstruction}
                  onChange={(e) => {
                    setCustomInstruction(e.target.value);
                    setRefineMsg(null);
                  }}
                  placeholder="Своё указание, например: «добавь срок ответа — 5 рабочих дней»"
                  className="min-w-56 flex-1 rounded-lg border border-line bg-white px-3 py-2 text-xs outline-none focus:border-accent-500"
                />
                <button
                  type="button"
                  onClick={() => sendRefine(customInstruction)}
                  disabled={Boolean(refining) || !customInstruction.trim()}
                  className={[
                    "rounded-lg px-3.5 py-2 text-xs font-medium text-white transition-colors",
                    refining || !customInstruction.trim() ? "bg-ink-300 cursor-not-allowed" : "bg-accent-600 hover:bg-accent-500",
                  ].join(" ")}
                >
                  {refining && !REFINE_CHIPS.some((c) => c.instruction === refining) ? "⏳ Применяем…" : "Применить"}
                </button>
              </div>
              {refineMsg && (
                <p className={["mt-2 text-xs", refineMsg.err ? "text-danger-500" : "text-success-600"].join(" ")}>
                  {refineMsg.text}
                </p>
              )}
            </div>
          )}
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
        <StampPad compact />
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

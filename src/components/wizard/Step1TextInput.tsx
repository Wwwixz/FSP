import { useEffect, useMemo, useState } from "react";
import type { DemoDraftDto, DocumentTypeId, InputMode } from "../../types/wizard";
import { DOCUMENT_TYPES } from "../../types/wizard";

const MAX_LENGTH = 5000;

interface Step1Props {
  inputMode: InputMode;
  rawText: string;
  onModeChange: (mode: InputMode) => void;
  onTextChange: (text: string) => void;
  onDocumentTypeHint?: (id: DocumentTypeId | undefined) => void;
  onNext: () => void;
}

export default function Step1TextInput({
  inputMode,
  rawText,
  onModeChange,
  onTextChange,
  onDocumentTypeHint,
  onNext,
}: Step1Props) {
  const [drafts, setDrafts] = useState<DemoDraftDto[]>([]);
  const [draftsError, setDraftsError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await fetch(`/api/demo-drafts`);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const json = (await res.json()) as DemoDraftDto[];
        if (!cancelled) setDrafts(json);
      } catch (e) {
        if (!cancelled) setDraftsError(String(e));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const grouped = useMemo(() => {
    const map = new Map<DocumentTypeId, DemoDraftDto[]>();
    for (const d of drafts) {
      if (!map.has(d.documentType)) map.set(d.documentType, []);
      map.get(d.documentType)!.push(d);
    }
    return map;
  }, [drafts]);

  const handlePasteFromClipboard = async () => {
    onModeChange("clipboard");
    try {
      const text = await navigator.clipboard.readText();
      if (text) onTextChange(text.slice(0, MAX_LENGTH));
    } catch {
      // Пользователь может вставить вручную
    }
  };

  const handlePickDraft = (draft: DemoDraftDto) => {
    onTextChange(draft.text.slice(0, MAX_LENGTH));
    onDocumentTypeHint?.(draft.documentType);
  };

  return (
    <section>
      <h2 className="text-lg font-medium text-ink-900">Ввод текста</h2>

      <div className="mt-4 inline-flex rounded-lg bg-surface p-1">
        <button
          type="button"
          onClick={() => onModeChange("manual")}
          className={[
            "rounded-md px-4 py-1.5 text-sm transition-colors",
            inputMode === "manual"
              ? "bg-accent-50 text-accent-600 font-medium"
              : "text-ink-600 hover:text-ink-900",
          ].join(" ")}
        >
          Ввести вручную
        </button>
        <button
          type="button"
          onClick={handlePasteFromClipboard}
          className={[
            "rounded-md px-4 py-1.5 text-sm transition-colors",
            inputMode === "clipboard"
              ? "bg-accent-50 text-accent-600 font-medium"
              : "text-ink-600 hover:text-ink-900",
          ].join(" ")}
        >
          Вставить из буфера
        </button>
      </div>

      <div className="relative mt-4">
        <textarea
          value={rawText}
          onChange={(e) => onTextChange(e.target.value.slice(0, MAX_LENGTH))}
          placeholder="Опишите суть документа своими словами — мы поможем оформить его правильно."
          rows={8}
          className="w-full resize-none rounded-xl border border-line bg-white p-4 text-sm text-ink-900 placeholder:text-ink-400 focus:border-accent-500"
        />
        <span className="pointer-events-none absolute bottom-3 right-4 text-xs text-ink-400">
          {rawText.length}/{MAX_LENGTH}
        </span>
      </div>

      <div className="mt-6">
        <h3 className="text-sm font-medium text-ink-900">
          Или выберите готовый пример из стартовых материалов
        </h3>

        {loading && (
          <p className="mt-3 text-xs text-ink-400">Загрузка примеров…</p>
        )}
        {!loading && draftsError && (
          <p className="mt-3 text-xs text-danger-500">
            Не удалось загрузить примеры: {draftsError}
          </p>
        )}
        {!loading && !draftsError && drafts.length === 0 && (
          <p className="mt-3 text-xs text-ink-400">Примеры отсутствуют.</p>
        )}

        {!loading && !draftsError && drafts.length > 0 && (
          <div className="mt-3 grid gap-3">
            {DOCUMENT_TYPES.map((type) => {
              const list = grouped.get(type.id) ?? [];
              if (list.length === 0) return null;
              return (
                <div key={type.id}>
                  <p className="mb-2 text-xs font-medium uppercase tracking-wide text-ink-500">
                    {type.label}
                  </p>
                  <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-4">
                    {list.map((d) => {
                      const active = rawText === d.text;
                      return (
                        <button
                          key={d.id}
                          type="button"
                          onClick={() => handlePickDraft(d)}
                          className={[
                            "rounded-lg border p-2.5 text-left text-xs transition-colors",
                            active
                              ? "border-accent-500 bg-accent-50 text-accent-700"
                              : "border-line bg-white text-ink-700 hover:border-accent-300 hover:bg-accent-50/40",
                          ].join(" ")}
                          title={d.text}
                        >
                          <p className="line-clamp-2 font-medium leading-snug">
                            {d.title}
                          </p>
                          <p className="mt-1 line-clamp-3 text-[11px] leading-snug text-ink-500">
                            {d.text.replace(/\s+/g, " ").trim()}
                          </p>
                        </button>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      <button
        type="button"
        onClick={onNext}
        disabled={rawText.trim().length === 0}
        className="mt-6 w-full rounded-lg bg-accent-600 py-2.5 text-sm font-medium text-white transition-colors hover:bg-accent-500 disabled:cursor-not-allowed disabled:opacity-40"
      >
        Далее →
      </button>
    </section>
  );
}

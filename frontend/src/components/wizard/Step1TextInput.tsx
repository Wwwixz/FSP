import { useCallback, useEffect, useMemo, useState } from "react";
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
  const [uploading, setUploading] = useState(false);
  const [uploadMsg, setUploadMsg] = useState<{ text: string; err?: boolean } | null>(null);

  // Загрузка примеров с таймаутом: на медленном/помершем туннеле не висим вечно
  const loadDrafts = useCallback(async () => {
    setLoading(true);
    setDraftsError(null);
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 15000);
    try {
      const res = await fetch(`/api/demo-drafts`, { signal: controller.signal });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = (await res.json()) as DemoDraftDto[];
      setDrafts(json);
    } catch (e) {
      setDraftsError(
        e instanceof DOMException && e.name === "AbortError"
          ? "Сервер не отвечает дольше 15 секунд"
          : String(e),
      );
    } finally {
      clearTimeout(timer);
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDrafts();
  }, [loadDrafts]);

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

  const [pickedDraftId, setPickedDraftId] = useState<string | null>(null);

  /**
   * Полный текст примера догружается по клику (в списке только анонсы —
   * экономия трафика на медленных публичных туннелях).
   */
  const handlePickDraft = async (draft: DemoDraftDto) => {
    onDocumentTypeHint?.(draft.documentType);
    if (draft.text != null) {
      onTextChange(draft.text.slice(0, MAX_LENGTH));
      setPickedDraftId(draft.id);
      return;
    }
    setPickedDraftId(draft.id);
    try {
      const res = await fetch(`/api/demo-drafts/${draft.id}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = (await res.json()) as DemoDraftDto;
      onTextChange(String(json.text ?? "").slice(0, MAX_LENGTH));
    } catch {
      setDraftsError("Не удалось загрузить текст примера — попробуйте ещё раз");
      setPickedDraftId(null);
    }
  };

  /**
   * «Реанимация документа»: загружаем готовый DOCX/PDF/TXT, сервер извлекает
   * текст и определяет тип — дальше обычный конвейер (ИИ правит текст,
   * оформление идёт по шаблону с печатью и подписью).
   */
  const handleFile = async (file: File) => {
    const ok = /\.(docx|pdf|txt)$/i.test(file.name);
    if (!ok) {
      setUploadMsg({ text: "Поддерживаются файлы .docx, .pdf и .txt", err: true });
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setUploadMsg({ text: "Размер файла не должен превышать 10 МБ", err: true });
      return;
    }
    setUploading(true);
    setUploadMsg(null);
    onModeChange("upload");
    try {
      const form = new FormData();
      form.append("file", file);
      const res = await fetch(`/api/documents/extract`, { method: "POST", body: form });
      const json = await res.json();
      if (!res.ok || !json?.success) {
        setUploadMsg({ text: json?.error?.message ?? "Не удалось разобрать файл", err: true });
        return;
      }
      onTextChange(String(json.text ?? "").slice(0, MAX_LENGTH));
      const detected = json.documentType as DocumentTypeId | null;
      if (detected) onDocumentTypeHint?.(detected);
      setUploadMsg({
        text: [
          `✅ Текст извлечён (${String(json.text ?? "").length} символов)`,
          detected ? `· тип определён: ${DOCUMENT_TYPES.find((t) => t.id === detected)?.label}` : "",
          json.warning ? `· ${json.warning}` : "",
        ].join(" "),
      });
    } catch {
      setUploadMsg({ text: "Сервис разбора файлов недоступен — вставьте текст вручную", err: true });
    } finally {
      setUploading(false);
    }
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
        <button
          type="button"
          onClick={() => onModeChange("upload")}
          className={[
            "rounded-md px-4 py-1.5 text-sm transition-colors",
            inputMode === "upload"
              ? "bg-accent-50 text-accent-600 font-medium"
              : "text-ink-600 hover:text-ink-900",
          ].join(" ")}
        >
          📄 Есть готовый документ
        </button>
      </div>

      {inputMode === "upload" && (
        <div
          className="mt-4 rounded-xl border-2 border-dashed border-accent-200 bg-accent-50/40 p-6 text-center"
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => {
            e.preventDefault();
            const f = e.dataTransfer.files?.[0];
            if (f) handleFile(f);
          }}
        >
          <label className="flex cursor-pointer flex-col items-center gap-2">
            <span className="text-sm font-medium text-ink-900">
              {uploading ? "⏳ Разбираем документ…" : "Загрузите готовый документ — DOCX, PDF или TXT"}
            </span>
            <span className="text-xs text-ink-500">
              Извлечём текст, ИИ исправит ошибки и оформление, добавим печать и подпись.
              Или перетащите файл сюда.
            </span>
            <input
              type="file"
              accept=".docx,.pdf,.txt"
              className="sr-only"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) handleFile(f);
                e.target.value = "";
              }}
            />
          </label>
          {uploadMsg && (
            <p className={["mt-3 text-xs", uploadMsg.err ? "text-danger-500" : "text-success-600"].join(" ")}>
              {uploadMsg.text}
            </p>
          )}
        </div>
      )}

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
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <p className="text-xs text-danger-500">Не удалось загрузить примеры: {draftsError}</p>
            <button
              type="button"
              onClick={loadDrafts}
              className="rounded-lg border border-line px-3 py-1.5 text-xs font-medium text-ink-600 transition-colors hover:bg-surface"
            >
              ↻ Повторить
            </button>
          </div>
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
                      const active = pickedDraftId === d.id;
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
                          title={d.preview ?? undefined}
                        >
                          <p className="line-clamp-2 font-medium leading-snug">
                            {d.title}
                          </p>
                          <p className="mt-1 line-clamp-3 text-[11px] leading-snug text-ink-500">
                            {(d.preview ?? "").replace(/\s+/g, " ").trim()}
                            {active && <span className="text-accent-600"> ⏳</span>}
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

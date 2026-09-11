import type { InputMode } from "../../types/wizard";

const MAX_LENGTH = 5000;

interface Step1Props {
  inputMode: InputMode;
  rawText: string;
  onModeChange: (mode: InputMode) => void;
  onTextChange: (text: string) => void;
  onNext: () => void;
}

export default function Step1TextInput({
  inputMode,
  rawText,
  onModeChange,
  onTextChange,
  onNext,
}: Step1Props) {
  const handlePasteFromClipboard = async () => {
    onModeChange("clipboard");
    try {
      const text = await navigator.clipboard.readText();
      if (text) onTextChange(text.slice(0, MAX_LENGTH));
    } catch {
      // Буфер обмена недоступен — пользователь может ввести текст вручную.
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

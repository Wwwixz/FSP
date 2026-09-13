import { useEffect, useRef, useState } from "react";
import { loadProfile } from "../../lib/profile";

export const STAMP_STORAGE_KEY = "dochelper:stamp-image";

interface StampPadProps {
  compact?: boolean;
}

/**
 * Печать организации: загрузка картинки или генерация круглой печати
 * по наименованию организации и городу (из профиля).
 */
export default function StampPad({ compact = false }: StampPadProps) {
  const [current, setCurrent] = useState<string | null>(() => {
    if (typeof window === "undefined") return null;
    try {
      const v = window.localStorage.getItem(STAMP_STORAGE_KEY);
      return v && v.trim() ? v : null;
    } catch {
      return null;
    }
  });
  const [tab, setTab] = useState<"upload" | "generate">("generate");
  const [org, setOrg] = useState<string>(() => loadProfile().organization);
  const [city, setCity] = useState<string>(() => loadProfile().city);
  const [generating, setGenerating] = useState(false);
  const [msg, setMsg] = useState<{ text: string; err?: boolean } | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (msg) {
      const t = setTimeout(() => setMsg(null), 3500);
      return () => clearTimeout(t);
    }
  }, [msg]);

  const setLocal = (b64: string | null) => {
    try {
      if (b64) window.localStorage.setItem(STAMP_STORAGE_KEY, b64);
      else window.localStorage.removeItem(STAMP_STORAGE_KEY);
    } catch {
      /* ignore */
    }
    setCurrent(b64);
  };

  const showMsg = (text: string, err = false) => setMsg({ text, err });

  const processFile = (file: File) => {
    if (!file.type.startsWith("image/")) {
      showMsg("Можно загружать только изображения", true);
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      showMsg("Размер файла не должен превышать 5 МБ", true);
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      setLocal(reader.result as string);
      showMsg("✅ Печать сохранена");
    };
    reader.onerror = () => showMsg("Ошибка чтения файла", true);
    reader.readAsDataURL(file);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    const f = e.dataTransfer.files?.[0];
    if (f) processFile(f);
  };

  const generate = async () => {
    if (!org.trim()) {
      showMsg("Укажите организацию — на печати должно быть наименование", true);
      return;
    }
    setGenerating(true);
    try {
      const res = await fetch(`/api/stamp/preview`, {
        method: "POST",
        headers: { "Content-Type": "application/json; charset=utf-8" },
        body: JSON.stringify({ organization: org.trim(), city: city.trim() }),
      });
      const json = await res.json();
      if (!res.ok || !json?.dataUrl) {
        showMsg(json?.error?.message ?? "Не удалось сгенерировать печать", true);
        return;
      }
      setPreview(json.dataUrl as string);
      showMsg("Печать сгенерирована — сохраните её или сгенерируйте заново");
    } catch {
      showMsg("Сервис печати недоступен — попробуйте позже", true);
    } finally {
      setGenerating(false);
    }
  };

  const saveGenerated = () => {
    if (!preview) return;
    setLocal(preview);
    showMsg("✅ Печать сохранена — она будет вставлена рядом с подписью");
  };

  const tabBtn = (active: boolean) =>
    [
      "rounded-md px-3 py-1.5 text-xs font-medium transition-colors",
      active ? "bg-white shadow-sm ring-1 ring-ink-900/5 text-ink-900" : "text-ink-600 hover:bg-white/60",
    ].join(" ");

  return (
    <div className="rounded-xl border border-line bg-white p-4">
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="text-sm font-semibold text-ink-900">Печать организации {compact ? "" : "🖨"}</p>
          <p className="mt-0.5 text-xs text-ink-500">
            Загрузите скан печати или сгенерируйте по названию — она встанет рядом с подписью.
          </p>
        </div>
        {current && (
          <button
            type="button"
            onClick={() => {
              setLocal(null);
              setPreview(null);
              showMsg("Печать удалена");
            }}
            className="shrink-0 rounded-lg border border-line px-2.5 py-1.5 text-xs font-medium text-ink-600 transition-colors hover:bg-surface"
          >
            🗑 Удалить
          </button>
        )}
      </div>

      {current ? (
        <div className="mt-3 flex items-center gap-4 rounded-lg border border-line bg-surface/60 p-3">
          <img src={current} alt="Печать организации" className="h-20 w-20 rounded-full object-contain" />
          <p className="text-xs text-ink-500">Печать добавляется в блок подписи документа.</p>
        </div>
      ) : (
        <div className="mt-3">
          <div className="inline-flex rounded-lg border border-line bg-surface p-1">
            <button type="button" onClick={() => setTab("generate")} className={tabBtn(tab === "generate")}>
              ✨ Сгенерировать
            </button>
            <button type="button" onClick={() => setTab("upload")} className={tabBtn(tab === "upload")}>
              📤 Загрузить
            </button>
          </div>

          {tab === "generate" && (
            <div className="mt-3 space-y-3">
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                <input
                  type="text"
                  value={org}
                  onChange={(e) => setOrg(e.target.value)}
                  placeholder="ООО «Ромашка»"
                  className="w-full rounded-lg border border-line bg-white px-3 py-2 text-sm outline-none focus:border-accent-500"
                />
                <input
                  type="text"
                  value={city}
                  onChange={(e) => setCity(e.target.value)}
                  placeholder="г. Красноярск"
                  className="w-full rounded-lg border border-line bg-white px-3 py-2 text-sm outline-none focus:border-accent-500"
                />
              </div>
              <button
                type="button"
                onClick={generate}
                disabled={generating}
                className={[
                  "rounded-lg px-4 py-2 text-sm font-medium text-white transition-colors",
                  generating ? "bg-ink-300 cursor-wait" : "bg-accent-600 hover:bg-accent-500",
                ].join(" ")}
              >
                {generating ? "Генерируем…" : "Сгенерировать печать"}
              </button>
              {preview && (
                <div className="flex items-center gap-4 rounded-lg border border-accent-200 bg-accent-50/50 p-3">
                  <img src={preview} alt="Предпросмотр печати" className="h-20 w-20 rounded-full object-contain" />
                  <button
                    type="button"
                    onClick={saveGenerated}
                    className="rounded-lg bg-accent-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-accent-500"
                  >
                    Сохранить печать
                  </button>
                </div>
              )}
            </div>
          )}

          {tab === "upload" && (
            <div
              className="mt-3 rounded-xl border border-dashed border-line bg-surface/60 p-5"
              onDragOver={(e) => e.preventDefault()}
              onDrop={handleDrop}
            >
              <label className="flex cursor-pointer flex-col items-center gap-2 text-center">
                <span className="text-sm font-medium text-ink-900">Нажмите или перетащите файл</span>
                <span className="text-xs text-ink-500">PNG, JPG — до 5 МБ</span>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="image/png,image/jpeg,image/jpg"
                  className="sr-only"
                  onChange={(e) => {
                    const f = e.target.files?.[0];
                    if (f) processFile(f);
                  }}
                />
              </label>
            </div>
          )}
        </div>
      )}

      {msg && (
        <p className={["mt-2 text-xs", msg.err ? "text-danger-500" : "text-success-600"].join(" ")}>
          {msg.text}
        </p>
      )}
    </div>
  );
}

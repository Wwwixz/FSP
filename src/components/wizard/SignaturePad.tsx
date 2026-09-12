import { useEffect, useRef, useState } from "react";

export const SIGNATURE_STORAGE_KEY = "dochelper:signature-image";

interface SignaturePadProps {
  compact?: boolean;
}

type Tab = "upload" | "draw";

export default function SignaturePad({ compact = false }: SignaturePadProps) {
  const [tab, setTab] = useState<Tab>("upload");
  const [currentSig, setCurrentSig] = useState<string | null>(() => {
    if (typeof window === "undefined") return null;
    try {
      const v = window.localStorage.getItem(SIGNATURE_STORAGE_KEY);
      return v && v.trim() ? v : null;
    } catch {
      return null;
    }
  });
  const [hasStrokes, setHasStrokes] = useState(false);
  const [penSize, setPenSize] = useState(3);
  const [penColor, setPenColor] = useState("#0f172a");
  const [msg, setMsg] = useState<{ text: string; err?: boolean } | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const drawState = useRef<{
    drawing: boolean;
    lastX: number;
    lastY: number;
    history: ImageData[];
  }>({ drawing: false, lastX: 0, lastY: 0, history: [] });
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (msg) {
      const t = setTimeout(() => setMsg(null), 3000);
      return () => clearTimeout(t);
    }
  }, [msg]);

  const setLocal = (b64: string | null) => {
    try {
      if (b64) window.localStorage.setItem(SIGNATURE_STORAGE_KEY, b64);
      else window.localStorage.removeItem(SIGNATURE_STORAGE_KEY);
    } catch {
      /* ignore */
    }
    setCurrentSig(b64);
  };

  const showMsg = (text: string, err = false) => setMsg({ text, err });

  // --- Upload ---
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
      const v = reader.result as string;
      setLocal(v);
      showMsg("✅ Подпись сохранена");
    };
    reader.onerror = () => showMsg("Ошибка чтения файла", true);
    reader.readAsDataURL(file);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    const f = e.dataTransfer.files?.[0];
    if (f) processFile(f);
  };

  // --- Drawing ---
  const getPoint = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return { x: 0, y: 0 };
    const rect = canvas.getBoundingClientRect();
    const kx = canvas.width / rect.width;
    const ky = canvas.height / rect.height;
    return {
      x: (e.clientX - rect.left) * kx,
      y: (e.clientY - rect.top) * ky,
    };
  };

  const takeSnapshot = () => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext("2d");
    if (!canvas || !ctx) return;
    drawState.current.history.push(ctx.getImageData(0, 0, canvas.width, canvas.height));
    if (drawState.current.history.length > 30) drawState.current.history.shift();
  };

  const startStroke = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext("2d");
    if (!ctx || !canvas) return;
    takeSnapshot();
    drawState.current.drawing = true;
    const { x, y } = getPoint(e);
    drawState.current.lastX = x;
    drawState.current.lastY = y;
    setHasStrokes(true);
    ctx.beginPath();
    ctx.arc(x, y, penSize / 2, 0, Math.PI * 2);
    ctx.fillStyle = penColor;
    ctx.fill();
  };

  const moveStroke = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!drawState.current.drawing) return;
    const ctx = canvasRef.current?.getContext("2d");
    if (!ctx) return;
    const { x, y } = getPoint(e);
    ctx.strokeStyle = penColor;
    ctx.lineWidth = penSize;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.beginPath();
    ctx.moveTo(drawState.current.lastX, drawState.current.lastY);
    ctx.lineTo(x, y);
    ctx.stroke();
    drawState.current.lastX = x;
    drawState.current.lastY = y;
  };

  const endStroke = () => {
    drawState.current.drawing = false;
  };

  const clearCanvas = () => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext("2d");
    if (!canvas || !ctx) return;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    drawState.current.history = [];
    setHasStrokes(false);
  };

  const undoStroke = () => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext("2d");
    if (!canvas || !ctx || drawState.current.history.length === 0) return;
    const snap = drawState.current.history.pop()!;
    ctx.putImageData(snap, 0, 0);
    // Проверяем, осталось ли что-то нарисованное
    let any = false;
    const d = snap.data;
    for (let i = 3; i < d.length; i += 4) if (d[i] > 0) { any = true; break; }
    setHasStrokes(any);
  };

  const trimCanvasToContent = (src: HTMLCanvasElement): HTMLCanvasElement => {
    const ctx = src.getContext("2d")!;
    const w = src.width, h = src.height;
    const d = ctx.getImageData(0, 0, w, h).data;
    let minX = w, minY = h, maxX = 0, maxY = 0;
    let found = false;
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        const a = d[(y * w + x) * 4 + 3];
        if (a > 0) {
          found = true;
          if (x < minX) minX = x;
          if (x > maxX) maxX = x;
          if (y < minY) minY = y;
          if (y > maxY) maxY = y;
        }
      }
    }
    if (!found) return src;
    const pad = 10;
    minX = Math.max(0, minX - pad);
    minY = Math.max(0, minY - pad);
    maxX = Math.min(w - 1, maxX + pad);
    maxY = Math.min(h - 1, maxY + pad);
    const outW = maxX - minX + 1;
    const outH = maxY - minY + 1;
    const out = document.createElement("canvas");
    out.width = outW; out.height = outH;
    out.getContext("2d")!.drawImage(src, minX, minY, outW, outH, 0, 0, outW, outH);
    return out;
  };

  const saveDraw = () => {
    const canvas = canvasRef.current;
    if (!canvas || !hasStrokes) {
      showMsg("Сначала нарисуйте подпись", true);
      return;
    }
    try {
      const trimmed = trimCanvasToContent(canvas);
      setLocal(trimmed.toDataURL("image/png"));
      showMsg("✅ Подпись сохранена");
    } catch {
      showMsg("Не удалось сохранить подпись", true);
    }
  };

  const deleteSig = () => {
    setLocal(null);
    clearCanvas();
    showMsg("Подпись удалена");
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
          <h3 className="text-sm font-semibold text-ink-900">Подпись ✍️</h3>
          <p className="mt-0.5 text-xs text-ink-500">
            Загрузите фото или нарисуйте прямо здесь
          </p>
        </div>
      </div>

      {/* Current preview */}
      <div className="mb-4">
        {currentSig ? (
          <div className="flex items-start justify-between gap-3 rounded-lg border border-line bg-surface/60 p-3">
            <div className="min-w-0">
              <p className="text-xs font-medium text-ink-700 mb-2">Текущая подпись</p>
              <div className="rounded-md border border-line bg-white p-2 inline-block max-w-full">
                <img
                  src={currentSig}
                  alt="Подпись"
                  className="max-h-16 max-w-52 object-contain"
                />
              </div>
            </div>
            <button
              type="button"
              onClick={deleteSig}
              className="shrink-0 rounded-md border border-line px-2.5 py-1.5 text-xs font-medium text-ink-600 hover:bg-surface transition-colors"
            >
              🗑 Удалить
            </button>
          </div>
        ) : (
          <div className="rounded-lg border border-dashed border-line bg-surface/40 p-3 text-xs text-ink-500">
            ⚠️ Подпись не задана — в документе будет только текст подписи
          </div>
        )}
      </div>

      {/* Tabs */}
      <div className="inline-flex rounded-lg border border-line bg-surface p-1 mb-4">
        {(["upload", "draw"] as Tab[]).map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => setTab(t)}
            className={[
              "rounded-md px-3 py-1 text-xs font-medium transition-colors",
              tab === t
                ? "bg-white text-ink-900 shadow-sm ring-1 ring-ink-900/5"
                : "text-ink-600 hover:bg-white/60",
            ].join(" ")}
          >
            {t === "upload" ? "📤 Загрузить" : "✏️ Нарисовать"}
          </button>
        ))}
      </div>

      {/* Upload pane */}
      {tab === "upload" && (
        <div
          onDragOver={(e) => e.preventDefault()}
          onDrop={handleDrop}
          className="rounded-lg border border-dashed border-line bg-surface/60 p-5 flex flex-col items-center gap-3"
        >
          <label
            htmlFor="sig-pad-file-input"
            className="flex cursor-pointer flex-col items-center gap-2 text-center hover:bg-white/60 rounded-lg px-4 py-3 w-full transition-colors"
          >
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" className="text-accent-500">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" stroke="currentColor"
                    strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
              <polyline points="17 8 12 3 7 8" stroke="currentColor" strokeWidth="1.8"
                        strokeLinecap="round" strokeLinejoin="round"/>
              <line x1="12" y1="3" x2="12" y2="15" stroke="currentColor" strokeWidth="1.8"
                    strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            <p className="text-sm font-medium text-ink-900">Нажмите или перетащите файл</p>
            <p className="text-xs text-ink-500">PNG, JPG — до 5 МБ</p>
          </label>
          <input
            id="sig-pad-file-input"
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
      )}

      {/* Draw pane */}
      {tab === "draw" && (
        <div className="rounded-lg border border-line bg-surface/60 p-4 space-y-3">
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-2">
              <span className="text-xs text-ink-600 shrink-0">Цвет:</span>
              <input
                type="color"
                value={penColor}
                onChange={(e) => setPenColor(e.target.value)}
                className="h-7 w-9 rounded-md border border-line bg-white p-0.5 cursor-pointer"
              />
            </div>
            <div className="flex items-center gap-2">
              <span className="text-xs text-ink-600 shrink-0">Толщина:</span>
              <input
                type="range"
                min={1}
                max={9}
                value={penSize}
                onChange={(e) => setPenSize(Number(e.target.value))}
                className="h-2 w-24 accent-accent-500"
              />
              <span className="text-xs text-ink-500 w-4">{penSize}</span>
            </div>
            <div className="flex items-center gap-2 ml-auto">
              <button
                type="button"
                onClick={undoStroke}
                disabled={drawState.current.history.length === 0}
                className="rounded-md border border-line bg-white px-2.5 py-1.5 text-xs font-medium text-ink-600 hover:bg-surface transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                ↶ Отмена
              </button>
              <button
                type="button"
                onClick={clearCanvas}
                className="rounded-md border border-line bg-white px-2.5 py-1.5 text-xs font-medium text-ink-600 hover:bg-surface transition-colors"
              >
                🧹 Очистить
              </button>
            </div>
          </div>
          <div className="rounded-md border border-line bg-white shadow-inner p-1">
            <canvas
              ref={canvasRef}
              width={560}
              height={160}
              onPointerDown={startStroke}
              onPointerMove={moveStroke}
              onPointerUp={endStroke}
              onPointerLeave={endStroke}
              onPointerCancel={endStroke}
              className="w-full rounded bg-white cursor-crosshair touch-none block"
              style={{ aspectRatio: "560 / 160" }}
            />
          </div>
          <div className="flex justify-end">
            <button
              type="button"
              onClick={saveDraw}
              disabled={!hasStrokes}
              className="rounded-lg bg-accent-600 px-4 py-2 text-xs font-medium text-white hover:bg-accent-500 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              ✅ Сохранить
            </button>
          </div>
        </div>
      )}

      {msg && (
        <p className={["mt-3 text-xs min-h-[1rem]", msg.err ? "text-danger-600" : "text-success-600"].join(" ")}>
          {msg.text}
        </p>
      )}
    </div>
  );
}

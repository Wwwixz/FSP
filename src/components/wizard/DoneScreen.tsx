import { useEffect } from "react";

interface DoneScreenProps {
  onCreateNew: () => void;
  downloadUrl?: string;
  fileName?: string;
  documentId?: string;
  documentTypeLabel?: string;
}

function DocumentCheckIcon() {
  return (
    <svg width="34" height="34" viewBox="0 0 34 34" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <path
        d="M10 5H20L26 11V27C26 28.1 25.1 29 24 29H10C8.9 29 8 28.1 8 27V7C8 5.9 8.9 5 10 5Z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <path d="M19.5 5V11H25.5" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
      <circle cx="22" cy="23" r="6.5" fill="white" stroke="currentColor" strokeWidth="1.8" />
      <path d="M19.3 23.1L21.2 25L24.7 21.2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function FolderIllustration() {
  return (
    <svg viewBox="0 0 320 150" className="mx-auto w-full max-w-[280px]" aria-hidden="true">
      <path d="M0 120C50 95 90 140 160 118C230 96 270 138 320 112V150H0V120Z" fill="var(--color-accent-50)" />
      <rect x="118" y="46" width="66" height="82" rx="4" fill="white" stroke="var(--color-line)" strokeWidth="2" />
      <path d="M130 62H172M130 76H172M130 90H160" stroke="var(--color-accent-100)" strokeWidth="4" strokeLinecap="round" />
      <path
        d="M60 96C60 92.7 62.7 90 66 90H110L120 100H176C179.3 100 182 102.7 182 106V126C182 129.3 179.3 132 176 132H66C62.7 132 60 129.3 60 126V96Z"
        fill="var(--color-accent-600)"
      />
      <path
        d="M60 96C60 92.7 62.7 90 66 90H110L118 98H176C179.3 98 182 100.7 182 104V108H60V96Z"
        fill="var(--color-accent-500)"
      />
      <path
        d="M198 70C210 60 226 64 228 78C240 78 248 88 244 98C252 102 252 114 242 118H210C200 118 194 110 196 102C188 98 188 86 198 82V70Z"
        fill="var(--color-accent-100)"
      />
    </svg>
  );
}

export default function DoneScreen({
  onCreateNew,
  downloadUrl,
  fileName = "document.docx",
  documentId,
  documentTypeLabel = "Документ",
}: DoneScreenProps) {
  const href = downloadUrl ?? undefined;

  useEffect(() => {
    if (!documentId || typeof window === "undefined") return;
    try {
      const STORAGE_KEY = "dochelper_documents";
      const raw = window.localStorage.getItem(STORAGE_KEY);
      const docs = raw ? JSON.parse(raw) : [];
      if (Array.isArray(docs) && !docs.some((d) => d.id === documentId)) {
        docs.push({
          id: documentId,
          name: fileName,
          type: documentTypeLabel,
          date: new Date().toLocaleDateString("ru-RU"),
        });
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(docs));
      }
    } catch {
      // localStorage недоступен — просто пропускаем сохранение в список
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [documentId]);

  return (
    <div className="rounded-2xl border border-line bg-card px-6 py-14 text-center shadow-sm shadow-ink-900/[0.03]">
      <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-accent-50 text-accent-600">
        <DocumentCheckIcon />
      </div>

      <h1 className="mt-6 text-2xl font-semibold text-ink-900">Документ готов!</h1>
      <p className="mx-auto mt-2 max-w-xs text-sm text-ink-600">
        Вы можете скачать файл на своё устройство или создать новый документ.
      </p>

      <div className="mx-auto mt-7 flex max-w-xs flex-col gap-3">
        {href ? (
          <a
            href={href}
            download={fileName}
            className="flex items-center justify-center gap-2 rounded-lg bg-accent-600 py-2.5 text-sm font-medium text-white transition-colors hover:bg-accent-500"
          >
            <svg width="15" height="15" viewBox="0 0 14 14" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
              <path d="M7 1.5V9" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
              <path d="M4 6.5L7 9.5L10 6.5" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round" />
              <path d="M2.5 11.5H11.5" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
            </svg>
            Скачать {fileName}
          </a>
        ) : (
          <button
            type="button"
            disabled
            className="cursor-not-allowed rounded-lg bg-ink-200 py-2.5 text-sm font-medium text-ink-500"
          >
            Ссылка на скачивание недоступна
          </button>
        )}
        <button
          type="button"
          onClick={onCreateNew}
          className="rounded-lg border border-line py-2.5 text-sm font-medium text-accent-600 transition-colors hover:bg-accent-50"
        >
          Создать новый документ
        </button>
      </div>

      <button
        type="button"
        onClick={onCreateNew}
        className="mt-5 text-sm text-accent-600 hover:underline"
      >
        ← На главную
      </button>

      <div className="mt-10">
        <FolderIllustration />
      </div>
    </div>
  );
}

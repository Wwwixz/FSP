import { useEffect, useState } from "react";
import { getCorporateTemplates, type CorporateTemplate } from "../lib/scalability";

export default function CorporateTemplatesList() {
  const [templates, setTemplates] = useState<CorporateTemplate[]>([]);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    setTemplates(getCorporateTemplates());
    setLoaded(true);
  }, []);

  if (!loaded) return null;

  return (
    <div className="mt-6">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-ink-900">Корпоративные шаблоны</h2>
        <a href="/settings" className="text-xs font-medium text-accent-600 hover:underline">
          + Добавить в настройках
        </a>
      </div>

      {templates.length === 0 ? (
        <p className="mt-3 text-sm text-ink-500">
          Пока нет ни одного корпоративного шаблона. Добавьте фирменный бланк компании на странице «Настройки».
        </p>
      ) : (
        <div className="stagger-children mt-3 grid grid-cols-1 gap-4 sm:grid-cols-2">
          {templates.map((t) => (
            <div
              key={t.id}
              className="card-hover group rounded-xl border-2 border-line bg-white p-5 transition-all duration-200 hover:border-accent-200"
            >
              <div className="flex items-start justify-between">
                <div>
                  <span className="inline-flex items-center rounded-md bg-accent-100 px-2 py-0.5 text-xs font-medium text-accent-600">
                    Корпоративный
                  </span>
                  <h3 className="mt-2 text-base font-semibold text-ink-900">{t.title}</h3>
                </div>
                {t.logoDataUrl ? (
                  <img src={t.logoDataUrl} alt="" className="h-10 w-10 rounded-xl object-contain" />
                ) : (
                  <div
                    className="flex h-10 w-10 items-center justify-center rounded-xl text-white transition-transform duration-200 group-hover:scale-110"
                    style={{ backgroundColor: t.accentColor }}
                  >
                    <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                      <rect x="3" y="2" width="14" height="16" rx="2" stroke="currentColor" strokeWidth="1.4" />
                      <path d="M6 6H14M6 9H14M6 12H10" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
                    </svg>
                  </div>
                )}
              </div>
              <p className="mt-3 text-sm leading-relaxed text-ink-600">{t.description}</p>
              <a
                href="/"
                className="mt-4 inline-flex items-center gap-1.5 text-sm font-medium text-accent-600 transition-colors hover:text-accent-500"
              >
                Использовать
                <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                  <path d="M5 3L9 7L5 11" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </a>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

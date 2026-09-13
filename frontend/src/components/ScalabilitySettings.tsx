import { useEffect, useState } from "react";
import {
  addCorporateTemplate,
  addCustomDocType,
  clearSedIntegration,
  getCorporateTemplates,
  getCustomDocTypes,
  getSedIntegration,
  removeCorporateTemplate,
  removeCustomDocType,
  saveSedIntegration,
  type CorporateTemplate,
  type CustomDocType,
  type SedIntegration,
} from "../lib/scalability";

const BASE_TEMPLATES: { id: "standard" | "modern"; label: string }[] = [
  { id: "standard", label: "Классический корпоративный" },
  { id: "modern", label: "Современный регламентный" },
];

const BASE_DOC_TYPES: { id: CustomDocType["baseTypeId"]; label: string }[] = [
  { id: "memo", label: "Служебная записка" },
  { id: "report", label: "Докладная записка" },
  { id: "certificate", label: "Информационная справка" },
  { id: "letter", label: "Письмо" },
];

function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

function maskKey(key: string): string {
  if (key.length <= 4) return "•".repeat(key.length);
  return `${"•".repeat(key.length - 4)}${key.slice(-4)}`;
}

export default function ScalabilitySettings() {
  const [templates, setTemplates] = useState<CorporateTemplate[]>([]);
  const [docTypes, setDocTypes] = useState<CustomDocType[]>([]);
  const [sed, setSed] = useState<SedIntegration | null>(null);
  const [loaded, setLoaded] = useState(false);

  const [templateTitle, setTemplateTitle] = useState("");
  const [templateBase, setTemplateBase] = useState<"standard" | "modern">("standard");
  const [templateColor, setTemplateColor] = useState("#2563eb");
  const [templateLogo, setTemplateLogo] = useState<string | undefined>(undefined);

  const [typeLabel, setTypeLabel] = useState("");
  const [typeBase, setTypeBase] = useState<CustomDocType["baseTypeId"]>("letter");

  const [sedSystem, setSedSystem] = useState("");
  const [sedUrl, setSedUrl] = useState("");
  const [sedKey, setSedKey] = useState("");

  useEffect(() => {
    setTemplates(getCorporateTemplates());
    setDocTypes(getCustomDocTypes());
    setSed(getSedIntegration());
    setLoaded(true);
  }, []);

  const handleAddTemplate = (e: React.FormEvent) => {
    e.preventDefault();
    if (!templateTitle.trim()) return;
    const next = addCorporateTemplate({
      title: templateTitle.trim(),
      description: `На основе шаблона «${BASE_TEMPLATES.find((t) => t.id === templateBase)?.label}», фирменный цвет ${templateColor}.`,
      baseTemplateId: templateBase,
      accentColor: templateColor,
      logoDataUrl: templateLogo,
    });
    setTemplates(next);
    setTemplateTitle("");
    setTemplateLogo(undefined);
  };

  const handleAddType = (e: React.FormEvent) => {
    e.preventDefault();
    if (!typeLabel.trim()) return;
    const next = addCustomDocType({ label: typeLabel.trim(), baseTypeId: typeBase });
    setDocTypes(next);
    setTypeLabel("");
  };

  const handleSaveSed = (e: React.FormEvent) => {
    e.preventDefault();
    if (!sedSystem.trim() || !sedUrl.trim() || !sedKey.trim()) return;
    const cfg = saveSedIntegration({
      systemName: sedSystem.trim(),
      apiUrl: sedUrl.trim(),
      apiKey: sedKey.trim(),
    });
    setSed(cfg);
    setSedSystem("");
    setSedUrl("");
    setSedKey("");
  };

  const handleDisconnectSed = () => {
    clearSedIntegration();
    setSed(null);
  };

  if (!loaded) return null;

  return (
    <div className="space-y-6">
      {/* Корпоративные шаблоны */}
      <div className="rounded-2xl border border-line bg-card p-6 shadow-sm shadow-ink-900/[0.03]">
        <h2 className="text-lg font-semibold text-ink-900">Корпоративные шаблоны</h2>
        <p className="mt-1 text-sm text-ink-600">
          Добавьте фирменный шаблон — он появится в галерее «Шаблоны» и будет доступен при создании документа.
        </p>

        {templates.length > 0 && (
          <ul className="mt-4 space-y-2">
            {templates.map((t) => (
              <li key={t.id} className="flex items-center gap-3 rounded-xl border border-line p-3">
                {t.logoDataUrl ? (
                  <img src={t.logoDataUrl} alt="" className="h-9 w-9 shrink-0 rounded-lg object-contain" />
                ) : (
                  <span
                    className="h-9 w-9 shrink-0 rounded-lg"
                    style={{ backgroundColor: t.accentColor, opacity: 0.85 }}
                  />
                )}
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-ink-900">{t.title}</p>
                  <p className="truncate text-xs text-ink-500">{t.description}</p>
                </div>
                <button
                  type="button"
                  onClick={() => setTemplates(removeCorporateTemplate(t.id))}
                  aria-label="Удалить шаблон"
                  className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-ink-400 transition-colors hover:bg-danger-50 hover:text-danger-500"
                >
                  <svg width="15" height="15" viewBox="0 0 14 14" fill="none">
                    <path d="M3.5 3.5L10.5 10.5M10.5 3.5L3.5 10.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
                  </svg>
                </button>
              </li>
            ))}
          </ul>
        )}

        <form onSubmit={handleAddTemplate} className="mt-4 space-y-3 rounded-xl bg-surface p-4">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <label className="text-xs font-medium text-ink-600">Название шаблона</label>
              <input
                type="text"
                value={templateTitle}
                onChange={(e) => setTemplateTitle(e.target.value)}
                placeholder="Например: Бланк ООО «Компания»"
                className="mt-1.5 w-full rounded-lg border border-line bg-white px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent-500"
              />
            </div>
            <div>
              <label className="text-xs font-medium text-ink-600">На основе шаблона</label>
              <select
                value={templateBase}
                onChange={(e) => setTemplateBase(e.target.value as "standard" | "modern")}
                className="mt-1.5 w-full rounded-lg border border-line bg-white px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent-500"
              >
                {BASE_TEMPLATES.map((t) => (
                  <option key={t.id} value={t.id}>{t.label}</option>
                ))}
              </select>
            </div>
          </div>
          <div className="flex flex-wrap items-end gap-4">
            <div>
              <label className="text-xs font-medium text-ink-600">Фирменный цвет</label>
              <input
                type="color"
                value={templateColor}
                onChange={(e) => setTemplateColor(e.target.value)}
                className="mt-1.5 h-9 w-14 cursor-pointer rounded-lg border border-line bg-white p-1"
              />
            </div>
            <div className="flex-1">
              <label className="text-xs font-medium text-ink-600">Логотип (PNG/JPG, необязательно)</label>
              <input
                type="file"
                accept="image/png,image/jpeg"
                onChange={async (e) => {
                  const file = e.target.files?.[0];
                  if (file) setTemplateLogo(await fileToDataUrl(file));
                }}
                className="mt-1.5 w-full text-xs text-ink-600 file:mr-3 file:rounded-lg file:border-0 file:bg-accent-50 file:px-3 file:py-1.5 file:text-xs file:font-medium file:text-accent-600"
              />
            </div>
            <button
              type="submit"
              className="rounded-lg bg-accent-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-accent-500"
            >
              + Добавить шаблон
            </button>
          </div>
        </form>
      </div>

      {/* Расширение типов документов */}
      <div className="rounded-2xl border border-line bg-card p-6 shadow-sm shadow-ink-900/[0.03]">
        <h2 className="text-lg font-semibold text-ink-900">Расширение типов документов</h2>
        <p className="mt-1 text-sm text-ink-600">
          Добавьте свой вид документа — он появится в шаге «Выбор типа и шаблона» при создании документа.
        </p>

        {docTypes.length > 0 && (
          <ul className="mt-4 space-y-2">
            {docTypes.map((d) => (
              <li key={d.id} className="flex items-center justify-between gap-3 rounded-xl border border-line p-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-ink-900">{d.label}</p>
                  <p className="truncate text-xs text-ink-500">
                    На основе: {BASE_DOC_TYPES.find((b) => b.id === d.baseTypeId)?.label}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setDocTypes(removeCustomDocType(d.id))}
                  aria-label="Удалить тип документа"
                  className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-ink-400 transition-colors hover:bg-danger-50 hover:text-danger-500"
                >
                  <svg width="15" height="15" viewBox="0 0 14 14" fill="none">
                    <path d="M3.5 3.5L10.5 10.5M10.5 3.5L3.5 10.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
                  </svg>
                </button>
              </li>
            ))}
          </ul>
        )}

        <form onSubmit={handleAddType} className="mt-4 flex flex-wrap items-end gap-3 rounded-xl bg-surface p-4">
          <div className="min-w-[180px] flex-1">
            <label className="text-xs font-medium text-ink-600">Название типа</label>
            <input
              type="text"
              value={typeLabel}
              onChange={(e) => setTypeLabel(e.target.value)}
              placeholder="Например: Договор"
              className="mt-1.5 w-full rounded-lg border border-line bg-white px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent-500"
            />
          </div>
          <div className="min-w-[200px]">
            <label className="text-xs font-medium text-ink-600">Оформлять по образцу</label>
            <select
              value={typeBase}
              onChange={(e) => setTypeBase(e.target.value as CustomDocType["baseTypeId"])}
              className="mt-1.5 w-full rounded-lg border border-line bg-white px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent-500"
            >
              {BASE_DOC_TYPES.map((t) => (
                <option key={t.id} value={t.id}>{t.label}</option>
              ))}
            </select>
          </div>
          <button
            type="submit"
            className="rounded-lg bg-accent-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-accent-500"
          >
            + Добавить тип
          </button>
        </form>
      </div>

      {/* Интеграция с СЭД */}
      <div className="rounded-2xl border border-line bg-card p-6 shadow-sm shadow-ink-900/[0.03]">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-semibold text-ink-900">Интеграция с СЭД</h2>
          {sed && (
            <span className="flex items-center gap-1.5 rounded-full bg-success-50 px-2.5 py-1 text-xs font-medium text-success-600">
              <svg width="12" height="12" viewBox="0 0 16 16" fill="none">
                <circle cx="8" cy="8" r="7" fill="currentColor" fillOpacity="0.2" />
                <path d="M5 8.2L7.1 10.3L11.2 5.9" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
              Настроено
            </span>
          )}
        </div>
        <p className="mt-1 text-sm text-ink-600">
          Подключите систему электронного документооборота, чтобы отправлять готовые документы прямо из DocHelper.
        </p>

        {sed ? (
          <div className="mt-4 rounded-xl border border-line p-4">
            <div className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-3">
              <div>
                <p className="text-xs text-ink-500">Система</p>
                <p className="font-medium text-ink-900">{sed.systemName}</p>
              </div>
              <div>
                <p className="text-xs text-ink-500">Адрес API</p>
                <p className="truncate font-medium text-ink-900">{sed.apiUrl}</p>
              </div>
              <div>
                <p className="text-xs text-ink-500">Ключ доступа</p>
                <p className="font-medium text-ink-900">{maskKey(sed.apiKey)}</p>
              </div>
            </div>
            <button
              type="button"
              onClick={handleDisconnectSed}
              className="mt-3 rounded-lg border border-line px-3 py-1.5 text-xs font-medium text-ink-600 transition-colors hover:bg-surface"
            >
              Отключить интеграцию
            </button>
          </div>
        ) : (
          <form onSubmit={handleSaveSed} className="mt-4 space-y-3 rounded-xl bg-surface p-4">
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <div>
                <label className="text-xs font-medium text-ink-600">Название системы</label>
                <input
                  type="text"
                  value={sedSystem}
                  onChange={(e) => setSedSystem(e.target.value)}
                  placeholder="Directum, 1С:Документооборот…"
                  className="mt-1.5 w-full rounded-lg border border-line bg-white px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent-500"
                />
              </div>
              <div>
                <label className="text-xs font-medium text-ink-600">Адрес API</label>
                <input
                  type="text"
                  value={sedUrl}
                  onChange={(e) => setSedUrl(e.target.value)}
                  placeholder="https://sed.company.ru/api"
                  className="mt-1.5 w-full rounded-lg border border-line bg-white px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent-500"
                />
              </div>
              <div>
                <label className="text-xs font-medium text-ink-600">Ключ доступа</label>
                <input
                  type="password"
                  value={sedKey}
                  onChange={(e) => setSedKey(e.target.value)}
                  placeholder="••••••••••••"
                  className="mt-1.5 w-full rounded-lg border border-line bg-white px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent-500"
                />
              </div>
            </div>
            <button
              type="submit"
              className="rounded-lg bg-accent-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-accent-500"
            >
              Сохранить и подключить
            </button>
          </form>
        )}
      </div>
    </div>
  );
}

import { useCallback, useMemo, useState } from "react";
import Stepper from "./Stepper";
import Step1TextInput from "./Step1TextInput";
import Step2TypeTemplate from "./Step2TypeTemplate";
import Step3Review from "./Step3Review";
import RequisitesForm from "./RequisitesForm";
import PreviewDocument from "./PreviewDocument";
import DoneScreen from "./DoneScreen";
import type {
  ApiError,
  DocumentTypeId,
  GenerateResponse,
  InputMode,
  ProcessResponse,
  RequisiteCheck,
  ResultPhase,
  StepId,
  TemplateId,
} from "../../types/wizard";
import { DOCUMENT_TYPES } from "../../types/wizard";

type RequisiteValues = Record<string, string>;

const DEFAULT_REQUISITES: RequisiteValues = {};
const SIGNATURE_STORAGE_KEY = "dochelper:signature-image";
const PHOTO_STORAGE_KEY = "dochelper:photo-image";

function getStoredSignature(): string | null {
  if (typeof window === "undefined") return null;
  try {
    const v = window.localStorage.getItem(SIGNATURE_STORAGE_KEY);
    return v && v.trim() ? v : null;
  } catch {
    return null;
  }
}

function getStoredPhoto(): string | null {
  if (typeof window === "undefined") return null;
  try {
    const v = window.localStorage.getItem(PHOTO_STORAGE_KEY);
    return v && v.trim() ? v : null;
  } catch {
    return null;
  }
}

function labelOfType(id: DocumentTypeId): string {
  return DOCUMENT_TYPES.find((t) => t.id === id)?.label ?? id;
}

function toClientChecks(resp: ProcessResponse): RequisiteCheck[] {
  return resp.checks.map((c) => ({
    key: c.key,
    label: c.label,
    status: (c.status === "done" ? "done" : "missing") as RequisiteCheck["status"],
    hint: c.hint ?? undefined,
  }));
}

function initialValuesFromResponse(resp: ProcessResponse): RequisiteValues {
  const vals: RequisiteValues = { ...DEFAULT_REQUISITES };
  const r = resp.requisites;
  if (r.number != null) vals.number = r.number;
  if (r.date != null) vals.date = r.date;
  if (r.signature != null) vals.signature = r.signature;
  if (r.recipient != null) vals.recipient = r.recipient;
  if (r.author != null) vals.author = r.author;
  if (r.subject != null) vals.subject = r.subject;
  if (r.salutation != null) vals.salutation = r.salutation;
  if (r.executor != null) vals.executor = r.executor;
  if (r.organization != null) vals.organization = r.organization;
  // А для тех, что в missing, пусть будет пустая строка, если нет
  for (const key of resp.missing) {
    if (!(key in vals) || vals[key] == null) vals[key] = "";
  }
  return vals;
}

export default function DocumentWizard() {
  const [view, setView] = useState<"wizard" | "done">("wizard");
  const [step, setStep] = useState<StepId>(1);
  const [resultPhase, setResultPhase] = useState<ResultPhase>("review");

  const [inputMode, setInputMode] = useState<InputMode>("manual");
  const [rawText, setRawText] = useState<string>(
    "",
  );
  const [documentType, setDocumentType] = useState<DocumentTypeId>("memo");
  const [template, setTemplate] = useState<TemplateId>("standard");

  // Loading + errors state
  const [isProcessing, setIsProcessing] = useState(false);
  const [isGenerating, setIsGenerating] = useState(false);
  const [aiError, setAiError] = useState<ApiError["error"] | null>(null);

  // Backend responses
  const [processResponse, setProcessResponse] = useState<ProcessResponse | null>(null);
  const [improvedText, setImprovedText] = useState<string>("");
  const [requisiteValues, setRequisiteValues] = useState<RequisiteValues>(DEFAULT_REQUISITES);
  const [generateResponse, setGenerateResponse] = useState<GenerateResponse | null>(null);

  // === API methods ===
  const callProcess = useCallback(async () => {
    setIsProcessing(true);
    setAiError(null);
    try {
      const res = await fetch(`/api/documents/process`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          text: rawText,
          documentType,
          templateId: template,
        }),
      });
      const json = await res.json();
      if (!res.ok) {
        const err = (json as ApiError).error;
        setAiError(err ?? { code: "UNKNOWN", message: "Неизвестная ошибка" });
        return;
      }
      const data = json as ProcessResponse;
      setProcessResponse(data);
      setImprovedText(data.improvedText);
      setRequisiteValues(initialValuesFromResponse(data));
      setResultPhase("review");
      setStep(3);
    } catch (e) {
      setAiError({
        code: "NETWORK",
        message:
          "Не удалось связаться с сервисом обработки. Проверьте подключение — введённый текст сохранён.",
      });
    } finally {
      setIsProcessing(false);
    }
  }, [rawText, documentType, template]);

  const callUpdateRequisites = useCallback(async () => {
    if (!processResponse) return;
    try {
      const signatureImage = getStoredSignature();
      const photoImage = getStoredPhoto();
      const res = await fetch(
        `/api/documents/${processResponse.documentId}/requisites`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            values: requisiteValues,
            signatureImage: signatureImage ?? undefined,
            photoImage: photoImage ?? undefined,
          }),
        },
      );
      const json = await res.json();
      if (!res.ok) return;
      const updated = json as ProcessResponse;
      setProcessResponse(updated);
      setRequisiteValues((prev) => {
        const next = { ...prev };
        const r = updated.requisites;
        if (r.number != null) next.number = r.number;
        if (r.date != null) next.date = r.date;
        if (r.signature != null) next.signature = r.signature;
        return next;
      });
    } catch {
      // ignore — пользователь может идти дальше
    }
  }, [processResponse, requisiteValues]);

  const callGenerate = useCallback(async () => {
    if (!processResponse) return;
    setIsGenerating(true);
    setAiError(null);
    try {
      const hasEdits = improvedText !== processResponse.improvedText;
      const signatureImage = getStoredSignature();
      const photoImage = getStoredPhoto();
      const body: Record<string, unknown> = {};
      if (hasEdits) body.finalText = improvedText;
      if (signatureImage) body.signatureImage = signatureImage;
      if (photoImage) body.photoImage = photoImage;
      const res = await fetch(
        `/api/documents/${processResponse.documentId}/generate`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        },
      );
      const json = await res.json();
      if (!res.ok) {
        setAiError(
          (json as ApiError).error ?? {
            code: "GENERATE_ERR",
            message: "Не удалось сгенерировать документ",
          },
        );
        return;
      }
      const data = json as GenerateResponse;
      setGenerateResponse(data);
      setResultPhase("preview");
    } catch (e) {
      setAiError({
        code: "NETWORK",
        message: "Не удалось сгенерировать файл — попробуйте ещё раз.",
      });
    } finally {
      setIsGenerating(false);
    }
  }, [processResponse, improvedText]);

  // === Navigation handlers ===
  const goToStep2 = () => setStep(2);
  const goToProcess = async () => {
    await callProcess();
  };
  const handleReviewNext = async () => {
    if (!processResponse) return;
    await callUpdateRequisites();
    const missing = (processResponse.missing ?? []).filter(
      (k) => !(requisiteValues[k] ?? "").trim(),
    );
    setResultPhase(missing.length > 0 ? "requisites" : "preview");
    // Если нет missing — сразу запустим генерацию (и перейдём в preview)
    if (missing.length === 0 && resultPhase === "review") {
      await callGenerate();
    }
  };
  const handleRequisitesNext = async () => {
    // Отправить на сервер значения и сразу сгенерировать
    await callUpdateRequisites();
    await callGenerate();
  };

  const handleCreateNew = () => {
    setView("wizard");
    setStep(1);
    setResultPhase("review");
    setRawText("");
    setDocumentType("memo");
    setTemplate("standard");
    setImprovedText("");
    setRequisiteValues(DEFAULT_REQUISITES);
    setProcessResponse(null);
    setGenerateResponse(null);
    setAiError(null);
  };

  const stepperValue: StepId =
    step === 3 && resultPhase === "requisites" ? 2 : step;

  const checksForUI = useMemo<RequisiteCheck[]>(
    () => (processResponse ? toClientChecks(processResponse) : []),
    [processResponse],
  );

  const previewRequisites = useMemo(
    () => (processResponse ? processResponse.requisites : {}),
    [processResponse],
  );

  // === Views ===
  if (view === "done") {
    return (
      <DoneScreen
        onCreateNew={handleCreateNew}
        downloadUrl={generateResponse?.downloadUrl}
        fileName={generateResponse?.fileName}
      />
    );
  }

  if (step === 3 && resultPhase === "preview") {
    return (
      <PreviewDocument
        improvedText={improvedText}
        documentTypeLabel={labelOfType(processResponse?.documentType ?? documentType)}
        requisites={previewRequisites}
        downloadUrl={generateResponse?.downloadUrl}
        fileName={generateResponse?.fileName}
        warnings={generateResponse?.warnings}
        onDownload={() => setView("done")}
      />
    );
  }

  return (
    <div className="rounded-2xl border border-line bg-card p-6 shadow-sm shadow-ink-900/[0.03]">
      <h1 className="text-xl font-semibold text-ink-900">Создание документа</h1>
      <p className="mt-1 text-sm text-ink-600">
        Подготовьте черновик, и мы поможем сделать его официальным и оформим
        по выбранному шаблону.
      </p>

      <div className="mt-5">
        <Stepper currentStep={stepperValue} />
      </div>

      {aiError && (
        <div className="mt-5 flex items-start gap-2.5 rounded-xl border border-danger-200 bg-danger-50 p-4 text-sm text-ink-900">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" className="mt-0.5 shrink-0 text-danger-500" aria-hidden="true">
            <circle cx="8" cy="8" r="7" fill="currentColor" fillOpacity="0.15" />
            <path d="M8 4.5V8.7" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
            <circle cx="8" cy="11.3" r="0.9" fill="currentColor" />
          </svg>
          <div>
            <p className="font-medium text-danger-700">
              [{aiError.code}] Ошибка обработки
            </p>
            <p className="mt-0.5">{aiError.message}</p>
            <p className="mt-2 text-xs text-ink-500">
              Введённый текст сохранён — вы можете исправить запросить обработку снова.
            </p>
          </div>
        </div>
      )}

      <div className="mt-6">
        {step === 1 && (
          <Step1TextInput
            inputMode={inputMode}
            rawText={rawText}
            onModeChange={setInputMode}
            onTextChange={setRawText}
            onDocumentTypeHint={(id) => id && setDocumentType(id)}
            onNext={goToStep2}
          />
        )}

        {step === 2 && (
          <div>
            <Step2TypeTemplate
              documentType={documentType}
              template={template}
              onDocumentTypeChange={setDocumentType}
              onTemplateChange={setTemplate}
              onBack={() => setStep(1)}
              onNext={goToProcess}
            />
            {isProcessing && (
              <div className="mt-4 rounded-xl bg-accent-50 p-4 text-sm text-accent-700">
                ⏳ Обрабатываем текст ИИ и проверяем реквизиты…
              </div>
            )}
          </div>
        )}

        {step === 3 && resultPhase === "review" && (
          <Step3Review
            improvedText={improvedText}
            onImprovedTextChange={setImprovedText}
            requisites={checksForUI}
            onBack={() => setStep(2)}
            onNext={handleReviewNext}
          />
        )}

        {step === 3 && resultPhase === "requisites" && (
          <div>
            <RequisitesForm
              checks={processResponse?.checks ?? []}
              values={requisiteValues}
              onChange={setRequisiteValues}
              onBack={() => setResultPhase("review")}
              onNext={handleRequisitesNext}
            />
            {isGenerating && (
              <div className="mt-4 rounded-xl bg-accent-50 p-4 text-sm text-accent-700">
                ⏳ Формируем DOCX по выбранному шаблону…
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

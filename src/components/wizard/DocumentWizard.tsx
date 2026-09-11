import { useState } from "react";
import Stepper from "./Stepper";
import Step1TextInput from "./Step1TextInput";
import Step2TypeTemplate from "./Step2TypeTemplate";
import Step3Review from "./Step3Review";
import RequisitesForm from "./RequisitesForm";
import PreviewDocument from "./PreviewDocument";
import DoneScreen from "./DoneScreen";
import type {
  DocumentTypeId,
  InputMode,
  RequisiteCheck,
  RequisiteValues,
  ResultPhase,
  StepId,
  TemplateId,
} from "../../types/wizard";

const SENDER_NAME = "Сидоров М.В.";

const DEFAULT_REQUISITES: RequisiteCheck[] = [
  { label: "ФИО отправителя", status: "done" },
  { label: "Должность отправителя", status: "done" },
  { label: "Подразделение", status: "done" },
  { label: "Дата", status: "done" },
  { label: "Исходящий номер", status: "missing", hint: "Не заполнено" },
  { label: "Подпись", status: "missing", hint: "Не заполнено" },
];

function improveText(text: string): string {
  // Заглушка для демо: в реальном приложении здесь будет вызов сервиса
  // нормализации текста. Пока просто убираем лишние пробелы и делаем
  // вежливую концовку чуть более официальной.
  return text
    .trim()
    .replace(/[ \t]+/g, " ")
    .replace(/Заранее спасибо\.?/i, "Заранее благодарю за рассмотрение.");
}

export default function DocumentWizard() {
  const [view, setView] = useState<"wizard" | "done">("wizard");
  const [step, setStep] = useState<StepId>(1);
  const [resultPhase, setResultPhase] = useState<ResultPhase>("review");

  const [inputMode, setInputMode] = useState<InputMode>("manual");
  const [rawText, setRawText] = useState(
    "Добрый день.\n\nПрошу рассмотреть вопрос о выделении дополнительного финансирования на проведение мероприятия.\n\nЗаранее спасибо.",
  );
  const [documentType, setDocumentType] = useState<DocumentTypeId>("memo");
  const [template, setTemplate] = useState<TemplateId>("standard");
  const [improvedText, setImprovedText] = useState("");
  const [requisiteValues, setRequisiteValues] = useState<RequisiteValues>({
    outgoingNumber: "",
    date: "22.08.2025",
    signatureName: "",
  });

  const goToStep2 = () => setStep(2);
  const goToStep3Review = () => {
    setImprovedText(improveText(rawText));
    setResultPhase("review");
    setStep(3);
  };

  const missingRequisites = DEFAULT_REQUISITES.filter((r) => r.status === "missing");

  const handleReviewNext = () => {
    setResultPhase(missingRequisites.length > 0 ? "requisites" : "preview");
  };

  const handleCreateNew = () => {
    setView("wizard");
    setStep(1);
    setResultPhase("review");
    setRawText("");
    setDocumentType("memo");
    setTemplate("standard");
    setImprovedText("");
    setRequisiteValues({ outgoingNumber: "", date: "22.08.2025", signatureName: "" });
  };

  // Степпер экрана «Заполните недостающие реквизиты» в макете указывает
  // на шаг 2 — заполнение реквизитов трактуется как продолжение выбора
  // шаблона, поскольку именно шаблон определяет обязательные поля.
  const stepperValue: StepId = step === 3 && resultPhase === "requisites" ? 2 : step;

  if (view === "done") {
    return <DoneScreen onCreateNew={handleCreateNew} />;
  }

  if (step === 3 && resultPhase === "preview") {
    return (
      <PreviewDocument
        improvedText={improvedText}
        requisites={requisiteValues}
        senderName={SENDER_NAME}
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

      <div className="mt-6">
        {step === 1 && (
          <Step1TextInput
            inputMode={inputMode}
            rawText={rawText}
            onModeChange={setInputMode}
            onTextChange={setRawText}
            onNext={goToStep2}
          />
        )}

        {step === 2 && (
          <Step2TypeTemplate
            documentType={documentType}
            template={template}
            onDocumentTypeChange={setDocumentType}
            onTemplateChange={setTemplate}
            onBack={() => setStep(1)}
            onNext={goToStep3Review}
          />
        )}

        {step === 3 && resultPhase === "review" && (
          <Step3Review
            improvedText={improvedText}
            onImprovedTextChange={setImprovedText}
            requisites={DEFAULT_REQUISITES}
            onBack={() => setStep(2)}
            onNext={handleReviewNext}
          />
        )}

        {step === 3 && resultPhase === "requisites" && (
          <RequisitesForm
            values={requisiteValues}
            onChange={setRequisiteValues}
            onBack={() => setResultPhase("review")}
            onNext={() => setResultPhase("preview")}
          />
        )}
      </div>
    </div>
  );
}

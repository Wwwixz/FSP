import { useState } from "react";
import Stepper from "./Stepper";
import Step1TextInput from "./Step1TextInput";
import Step2TypeTemplate from "./Step2TypeTemplate";
import Step3Review from "./Step3Review";
import type {
  DocumentTypeId,
  InputMode,
  RequisiteCheck,
  StepId,
  TemplateId,
} from "../../types/wizard";

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
  const [step, setStep] = useState<StepId>(1);
  const [inputMode, setInputMode] = useState<InputMode>("manual");
  const [rawText, setRawText] = useState(
    "Добрый день.\n\nПрошу рассмотреть вопрос о выделении дополнительного финансирования на проведение мероприятия.\n\nЗаранее спасибо.",
  );
  const [documentType, setDocumentType] = useState<DocumentTypeId>("memo");
  const [template, setTemplate] = useState<TemplateId>("standard");
  const [improvedText, setImprovedText] = useState("");

  const goToStep2 = () => setStep(2);
  const goToStep3 = () => {
    setImprovedText(improveText(rawText));
    setStep(3);
  };

  return (
    <div className="rounded-2xl border border-line bg-card p-6 shadow-sm shadow-ink-900/[0.03]">
      <h1 className="text-xl font-semibold text-ink-900">Создание документа</h1>
      <p className="mt-1 text-sm text-ink-600">
        Подготовьте черновик, и мы поможем сделать его официальным и оформим
        по выбранному шаблону.
      </p>

      <div className="mt-5">
        <Stepper currentStep={step} />
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
            onNext={goToStep3}
          />
        )}

        {step === 3 && (
          <Step3Review
            improvedText={improvedText}
            onImprovedTextChange={setImprovedText}
            requisites={DEFAULT_REQUISITES}
            onBack={() => setStep(2)}
            onNext={() => {
              // Следующий экран (заполнение реквизитов) — вне рамок первых 3 экранов.
            }}
          />
        )}
      </div>
    </div>
  );
}

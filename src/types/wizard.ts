export type StepId = 1 | 2 | 3;

export interface StepInfo {
  id: StepId;
  label: string;
}

export const STEPS: StepInfo[] = [
  { id: 1, label: "Ввод текста" },
  { id: 2, label: "Выбор типа и шаблона" },
  { id: 3, label: "Результат" },
];

export type InputMode = "manual" | "clipboard";

export type DocumentTypeId =
  | "memo"
  | "report"
  | "certificate"
  | "letter";

export interface DocumentTypeOption {
  id: DocumentTypeId;
  label: string;
}

export const DOCUMENT_TYPES: DocumentTypeOption[] = [
  { id: "memo", label: "Служебная записка" },
  { id: "report", label: "Докладная записка" },
  { id: "certificate", label: "Информационная справка" },
  { id: "letter", label: "Письмо" },
];

export type TemplateId = "standard" | "modern";

export interface TemplateOption {
  id: TemplateId;
  title: string;
  description: string;
}

export const TEMPLATES: TemplateOption[] = [
  {
    id: "standard",
    title: "Стандартный",
    description: "Классический строгий стиль. Подойдёт для большинства служебных записок.",
  },
  {
    id: "modern",
    title: "Современный",
    description: "Более лаконичный дизайн с современной вёрсткой и шрифтом.",
  },
];

export type RequisiteStatus = "done" | "missing";

export interface RequisiteCheck {
  label: string;
  status: RequisiteStatus;
  hint?: string;
}

export interface WizardState {
  step: StepId;
  inputMode: InputMode;
  rawText: string;
  documentType: DocumentTypeId;
  template: TemplateId;
  improvedText: string;
}

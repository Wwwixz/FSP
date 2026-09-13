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
    title: "Классический корпоративный",
    description:
      "Times New Roman 14 pt, интервал 1,5, абзацный отступ 1,25 см, выравнивание по ширине, верхний колонтитул с названием организации.",
  },
  {
    id: "modern",
    title: "Современный регламентный",
    description:
      "Arial 12 pt, интервал 1,15, табличная шапка «Кому / От кого», подпись по центру, нижний колонтитул с названием документа и датой.",
  },
];

export type RequisiteStatus = "done" | "missing";

export interface RequisiteCheck {
  key?: string;
  label: string;
  status: RequisiteStatus;
  hint?: string;
}

export interface RequisiteValues {
  outgoingNumber: string;
  date: string;
  signatureName: string;
}

export type ResultPhase = "review" | "requisites" | "preview";

export interface RequisiteDto {
  recipient?: string | null;
  author?: string | null;
  subject?: string | null;
  date?: string | null;
  number?: string | null;
  signature?: string | null;
  salutation?: string | null;
  executor?: string | null;
  organization?: string | null;
}

export interface RequisiteCheckDto {
  key: string;
  label: string;
  status: string;
  hint?: string | null;
}

export interface ProcessRequest {
  text: string;
  documentType: DocumentTypeId;
  templateId: TemplateId;
}

export interface ProcessResponse {
  documentId: string;
  improvedText: string;
  documentType: DocumentTypeId;
  templateId: TemplateId;
  requisites: RequisiteDto;
  checks: RequisiteCheckDto[];
  missing: string[];
}

export interface DemoDraftDto {
  id: string;
  documentType: DocumentTypeId;
  title: string;
  text: string;
}

export interface GenerateResponse {
  success: boolean;
  documentId: string;
  fileName: string;
  downloadUrl: string;
  warnings: string[];
}

export interface ApiError {
  success: boolean;
  error: {
    code: string;
    message: string;
  };
}

export type ApiResult<T> =
  | { ok: true; data: T }
  | { ok: false; error: ApiError["error"] };

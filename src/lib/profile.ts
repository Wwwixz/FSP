/**
 * Профиль пользователя (Настройки): единые данные автора документов.
 *
 * Хранятся в localStorage и автоматически подставляются в реквизиты
 * новых документов — в том числе при работе с демо-примерами,
 * чтобы в документах всегда были данные пользователя, а не «данные из болды».
 */

export interface UserProfile {
  fullName: string;
  position: string;
  department: string;
  organization: string;
  city: string;
  phone: string;
  email: string;
}

export const PROFILE_STORAGE_KEY = "dochelper:profile";

export const EMPTY_PROFILE: UserProfile = {
  fullName: "",
  position: "",
  department: "",
  organization: "",
  city: "",
  phone: "",
  email: "",
};

type LegacyRecord = Record<string, string>;

function pick(src: LegacyRecord, ...keys: string[]): string {
  for (const key of keys) {
    const value = src[key];
    if (typeof value === "string" && value.trim()) return value.trim();
  }
  return "";
}

/** Читает профиль; поддерживает и старый формат (ключи «full-name» и т.п.). */
export function loadProfile(): UserProfile {
  if (typeof window === "undefined") return { ...EMPTY_PROFILE };
  try {
    const raw = window.localStorage.getItem(PROFILE_STORAGE_KEY);
    if (!raw) return { ...EMPTY_PROFILE };
    const data = JSON.parse(raw) as LegacyRecord;
    return {
      fullName: pick(data, "fullName", "full-name"),
      position: pick(data, "position"),
      department: pick(data, "department"),
      organization: pick(data, "organization"),
      city: pick(data, "city"),
      phone: pick(data, "phone"),
      email: pick(data, "email"),
    };
  } catch {
    return { ...EMPTY_PROFILE };
  }
}

export function saveProfile(profile: UserProfile): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(PROFILE_STORAGE_KEY, JSON.stringify(profile));
  } catch {
    /* переполнение хранилища игнорируем */
  }
}

/**
 * Валидация «не из болды»: обязательные поля заполнены осмысленно,
 * необязательные — корректного формата. Возвращает ошибки по полям.
 */
export function validateProfile(profile: UserProfile): Partial<Record<keyof UserProfile, string>> {
  const errors: Partial<Record<keyof UserProfile, string>> = {};

  const name = profile.fullName.trim();
  if (!name) {
    errors.fullName = "Укажите ФИО — оно подставляется в реквизит «От кого» и подпись";
  } else {
    const words = name.split(/\s+/).filter((w) => /^[А-ЯЁа-яёA-Za-z-]{2,}$/.test(w));
    if (words.length < 2) {
      errors.fullName = "ФИО должно быть настоящим: минимум два слова по 2+ букв, например «Петров Иван»";
    }
  }

  if (profile.organization.trim().length < 2) {
    errors.organization = "Укажите организацию — она попадёт в шапку документа и печать";
  } else if (/^(.)\1+$/.test(profile.organization.trim())) {
    errors.organization = "Похоже на случайный набор символов";
  }

  const email = profile.email.trim();
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) {
    errors.email = "Формат e-mail: name@example.ru";
  }

  const phone = profile.phone.replace(/[^\d+]/g, "");
  if (profile.phone.trim() && (phone.length < 10 || phone.length > 12)) {
    errors.phone = "Телефон: 10–12 цифр, например +7 999 123-45-67";
  }

  return errors;
}

/** «Петров И.С.» из «Петров Иван Сергеевич». */
export function shortName(fullName: string): string {
  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  if (parts.length < 2) return fullName.trim();
  const [family, given, ...rest] = parts;
  const initials = [given, ...rest]
    .filter((w) => /^[А-ЯЁа-яёA-Za-z-]+$/.test(w))
    .map((w) => `${w[0].toUpperCase()}.`)
    .join("");
  return initials ? `${family} ${initials}` : fullName.trim();
}

/** Строка «От кого»: «должность ФИО». */
export function profileAuthorLine(profile: UserProfile): string {
  const position = profile.position.trim();
  const name = profile.fullName.trim();
  if (position && name) {
    const separator = /[.,!?]$/.test(position) ? " " : ", ";
    return `${position}${separator}${name}`;
  }
  return name || position;
}

/** Строка исполнителя: «ФИО, телефон». */
export function profileExecutorLine(profile: UserProfile): string {
  const name = profile.fullName.trim();
  const phone = profile.phone.trim();
  if (name && phone) return `${name}, ${phone}`;
  return name || phone;
}

/** Заполнен ли профиль хотя бы минимально (для подсказок в мастере). */
export function profileIsComplete(profile: UserProfile): boolean {
  return profile.fullName.trim().length > 0 && profile.organization.trim().length > 0;
}

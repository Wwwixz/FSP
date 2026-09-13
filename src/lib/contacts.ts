/**
 * Адресная книга получателей: сохранённые адресаты подставляются
 * в реквизиты одним кликом. Хранятся в localStorage (обезличенно,
 * в духе требований ТЗ к приватности).
 */

export const CONTACTS_STORAGE_KEY = "dochelper:contacts";
const MAX_CONTACTS = 20;

export function loadContacts(): string[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = window.localStorage.getItem(CONTACTS_STORAGE_KEY);
    const parsed = raw ? (JSON.parse(raw) as unknown) : [];
    return Array.isArray(parsed) ? parsed.filter((c): c is string => typeof c === "string") : [];
  } catch {
    return [];
  }
}

export function saveContact(line: string): string[] {
  const value = line.trim();
  if (!value) return loadContacts();
  const contacts = loadContacts().filter((c) => c !== value);
  contacts.unshift(value);
  const trimmed = contacts.slice(0, MAX_CONTACTS);
  persist(trimmed);
  return trimmed;
}

export function removeContact(line: string): string[] {
  const trimmed = loadContacts().filter((c) => c !== line);
  persist(trimmed);
  return trimmed;
}

function persist(contacts: string[]) {
  try {
    window.localStorage.setItem(CONTACTS_STORAGE_KEY, JSON.stringify(contacts));
  } catch {
    /* переполнение хранилища игнорируем */
  }
}

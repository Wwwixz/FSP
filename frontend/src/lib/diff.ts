/**
 * Пословное сравнение «до/после» для панели «Что исправил ИИ».
 *
 * Алгоритм: нормализуем токены (нижний регистр, без пунктуации) и строим
 * наибольшую общую подпоследовательность (LCS). Совпадающие токены — без
 * изменений, встречающиеся только в улучшенном тексте — добавления,
 * только в черновике — удаления.
 */

export interface DiffToken {
  type: "same" | "add" | "del";
  text: string;
}

export interface DiffResult {
  tokens: DiffToken[];
  added: number;
  removed: number;
}

function tokenize(text: string): string[] {
  return text.replace(/\r\n/g, "\n").split(/(\s+)/).filter((t) => t.length > 0);
}

function normalize(token: string): string {
  return token
    .toLowerCase()
    .replace(/[.,!?;:«»"()'`\[\]{}—–-]+/g, "")
    .trim();
}

/** LCS-таблица на нормализованных токенах. */
function lcsTable(a: string[], b: string[]): Int32Array {
  const n = a.length;
  const m = b.length;
  const table = new Int32Array((n + 1) * (m + 1));
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      table[i * (m + 1) + j] =
        a[i] === b[j]
          ? table[(i + 1) * (m + 1) + j + 1] + 1
          : Math.max(table[(i + 1) * (m + 1) + j], table[i * (m + 1) + j + 1]);
    }
  }
  return table;
}

/** Сравнивает черновик и улучшенный текст; tokens чередуют add/del/same. */
export function diffWords(before: string, after: string): DiffResult {
  const aTokens = tokenize(before);
  const bTokens = tokenize(after);
  const aNorm = aTokens.map(normalize);
  const bNorm = bTokens.map(normalize);

  const tokens: DiffToken[] = [];
  let added = 0;
  let removed = 0;
  let sameWords = 0;

  const push = (type: DiffToken["type"], text: string) => {
    const last = tokens[tokens.length - 1];
    if (last && last.type === type) {
      last.text += text;
    } else {
      tokens.push({ type, text });
    }
  };

  const table = lcsTable(aNorm, bNorm);
  const m = bNorm.length;
  let i = 0;
  let j = 0;
  while (i < aTokens.length && j < bTokens.length) {
    if (aNorm[i] === bNorm[j]) {
      push("same", bTokens[j]);
      if (aNorm[i] !== "") sameWords++;
      i++;
      j++;
    } else if (table[(i + 1) * (m + 1) + j] >= table[i * (m + 1) + j + 1]) {
      // токен был только в черновике — удалён (если это не чистый пробел)
      if (aNorm[i] !== "") removed++;
      push("del", aTokens[i] + (aTokens[i].trim() ? " " : ""));
      i++;
    } else {
      if (bNorm[j] !== "") added++;
      push("add", bTokens[j]);
      j++;
    }
  }
  while (i < aTokens.length) {
    if (aNorm[i] !== "") removed++;
    push("del", aTokens[i] + (aTokens[i].trim() ? " " : ""));
    i++;
  }
  while (j < bTokens.length) {
    if (bNorm[j] !== "") added++;
    push("add", bTokens[j]);
    j++;
  }

  return { tokens, added, removed, ...{ sameWords } };
}

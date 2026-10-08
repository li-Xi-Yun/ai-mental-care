/**
 * 大整数（Long）数值安全工具
 *
 * 后端主键等字段使用 Long，可能超出 Number.MAX_SAFE_INTEGER（2^53-1）。
 * 若在前端直接用 JSON.parse / Number 处理，会丢失精度（末尾数字被舍入）。
 *
 * 通用做法（不硬编码字段名）：
 *  - 解析前：先「隔离」JSON 中的所有字符串字面量，再对剩余位置出现的「绝对值 > MAX_SAFE_INTEGER」
 *    的整数数字加引号，最后 JSON.parse。这样无论字段叫什么、层级多深都能保护。
 *  - 递归对象：把任意超出安全范围的 number 转成 string；把「安全范围内」的数字字符串还原为 number，
 *    保证前后台数字类型一致。
 */

/** JS 可安全精确表示的最大整数 */
export const MAX_SAFE_INTEGER = Number.MAX_SAFE_INTEGER;

/** 匹配 JSON 字符串字面量（含转义），用于先隔离字符串中的数字 */
const JSON_STRING_LITERAL_RE = /"(?:\\.|[^"\\])*"/g;

/**
 * 判断数值是否超出 JS 安全整数范围（该值已逝去精度，只能按近似判断）
 */
export function isUnsafeInteger(value: unknown): boolean {
  if (typeof value === "number") {
    return Number.isFinite(value) && Math.abs(value) > MAX_SAFE_INTEGER;
  }
  if (typeof value === "string" && value.trim() !== "" && /^-?\d+$/.test(value.trim())) {
    const num = Number(value.trim());
    return !Number.isNaN(num) && Math.abs(num) > MAX_SAFE_INTEGER;
  }
  return false;
}

/** 超过安全范围的数字转字符串；否则保持原样 */
export function safeNumberToString(value: unknown): unknown {
  if (typeof value === "number" && isUnsafeInteger(value)) return value.toString();
  return value;
}

/** 将「安全范围内的纯数字字符串」还原为 number；超范围的数字字符串保持 string */
export function safeStringToNumber(value: unknown): unknown {
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (/^-?\d+$/.test(trimmed) && !/^0\d+$/.test(trimmed)) {
      const num = Number(trimmed);
      if (!Number.isNaN(num) && Number.isFinite(num) && Math.abs(num) <= MAX_SAFE_INTEGER) {
        return num;
      }
    }
  }
  return value;
}

/**
 * 递归处理对象/数组中的大整数与数字字符串。
 * @param mode  'parse' / 'stringify'：响应拿到对象阶段，把超范围 number → string；
 *              'restore'：请求发送前，把安全范围数字字符串 → number
 */
export function processBigIntFields(target: unknown, mode: "stringify" | "restore" = "stringify"): unknown {
  if (target === null || target === undefined) return target;
  if (Array.isArray(target)) {
    return target.map((item) => processBigIntFields(item, mode));
  }
  if (typeof target !== "object") {
    return mode === "restore" ? safeStringToNumber(target) : safeNumberToString(target);
  }

  const result: Record<string, unknown> = {};
  for (const key of Object.keys(target as Record<string, unknown>)) {
    result[key] = processBigIntFields((target as Record<string, unknown>)[key], mode as "stringify");
  }
  return result;
}

/**
 * 解析 JSON 文本，并保护超出安全范围的 Long 不丢失精度。
 * - 暂存 JSON 中的字符串字面量（防止误伤字符串里的数字）；
 * - 对剩余的纯整数 token 用 BigInt 判断是否超出安全范围；
 * - 超出则加引号，再 JSON.parse。
 */
export function parseJSONWithBigInt(text: string): unknown {
  if (typeof text !== "string" || text.trim() === "") return text;

  const stringLiterals: string[] = [];
  const masked = text.replace(JSON_STRING_LITERAL_RE, (match) => {
    stringLiterals.push(match);
    return `\u0000${stringLiterals.length - 1}\u0000`;
  });

  const protectedText = masked.replace(
    /(^|[:,[]\s*)(-?\d{15,})(?=\s*[,}\])]|$)/g,
    (full, prefix: string, num: string) => {
      try {
        const big = BigInt(num);
        if (big > BigInt(MAX_SAFE_INTEGER) || big < BigInt(-MAX_SAFE_INTEGER)) {
          return `${prefix}"${num}"`;
        }
      } catch {
        /* 非纯整数（如带小数/指数）不处理 */
      }
      return full;
    },
  );

  const restored = protectedText.replace(/\u0000(\d+)\u0000/g, (_, idx: string) => stringLiterals[Number(idx)] ?? "");

  try {
    return JSON.parse(restored);
  } catch {
    try {
      return JSON.parse(text);
    } catch {
      return text;
    }
  }
}

/** 序列化（配合 parseJSONWithBigInt 的可逆场景）其余场景直接用 JSON.stringify */
export function safeJSONStringify(value: unknown): string {
  return JSON.stringify(processBigIntFields(value, "restore"));
}

/** 对象安全比较（忽略类型差异，适合 Long 与字符串 id 比较） */
export function safeBigIntEqual(a: unknown, b: unknown): boolean {
  return String(a) === String(b);
}
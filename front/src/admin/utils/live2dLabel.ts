/**
 * Live2D 白名单中文含义解析工具（纯函数）
 *
 * 后台 `semantic_prompt` 历史上以「中文含义=资产id」描述表情/动作映射（如 `中性/平静=F01；开心/愉悦=F05`），
 * 但这是发给大模型的提示词，非结构化数据。本工具把它解析为「资产id → 中文含义」Map，
 * 供「从语义提示词导入」一键回填白名单中文标签，用 value 可编辑预览。
 *
 * 解析规则（宽松，兼容既有 9 个模型提示词格式）：
 * - 按中文分号 `；` / 半角分号 `;` / 换行分段；
 * - 每段中取 `=`（或 `→`）右侧、以字母数字开头、不含中文/空格/逗号的前缀作为候选资产 id；
 *   （`id=xxx。仅能...` 这类结尾带说明文字的段也能命中——匹配到 id 即停，不要求 id 在段尾）
 * - 候选 id 须 ∈ 白名单才收录；
 * 解析规则（兼容既有 9 个模型提示词格式）：
 * - 先以英文句点 `。`、`；`、`;` 切出「语义子句」；
 * - 每个子句中扫描所有 `=id` 片段：id 紧跟在 `=` 之后、以字母/数字开始、不含分隔符；
 *   （能命中 `愤怒=exp_08。动作映射：待机=mtn_01` 这类「本段末尾接着下段开头」的黏连子句）
 * - id 须 ∈ 白名单才收录；同一子句中多个映射 id 逐个收录；
 * - 取 `=id` 之前最近一段中文为含义：先剥离 `动作(motion)映射：` 等段前缀（最后一个 `：` 之前），
 *   再取 `/` 分隔同义项中的第一个。
 */

/** 取某个 index 之前的中文含义：以段分隔符（`；`/`，`/`、`/`。`）截出本映射的独立小段，再剥离段前缀「映射：」，取 `/` 同义项第一个 */
function labelBeforeEqual(text: string, equalIndex: number): string {
  const before = text.slice(0, equalIndex);
  // 段分隔符：截到最近一个之后，得到「本映射的中文小段」（避免跨段黏连取错前一个映射的中文）
  const segIdx = Math.max(
    before.lastIndexOf("；"),
    before.lastIndexOf(";"),
    before.lastIndexOf("，"),
    before.lastIndexOf("、"),
    before.lastIndexOf("。"),
  );
  let segment = segIdx >= 0 ? before.slice(segIdx + 1) : before;
  // 剥离段前缀：`动作(motion)映射：待机/默认` → `待机/默认`
  const mapIdx = segment.lastIndexOf("映射：");
  if (mapIdx >= 0) {
    segment = segment.slice(mapIdx + 3);
  }
  return segment.split("/")[0].trim().replace(/^[\s,、，。]+/u, "");
}

/** 解析 semanticPrompt 中的「中文=id」映射，仅收录出现在 whitelist 中的资产 id */
export function parseLabelMap(prompt: string, whitelist: string[]): Record<string, string> {
  const result: Record<string, string> = {};
  if (!prompt || !whitelist?.length) return result;

  // 先按句号切出子句，避免「上一映射值 + 下一段前缀」黏连覆盖
  const clauses = prompt
    .split(/[。]+/)
    .map((s) => s.trim())
    .filter(Boolean);

  for (const clause of clauses) {
    // 子句内扫描每个 =id：id 紧跟 =，字母数字开头
    const re = /[=＝]\s*([A-Za-z0-9_]+)/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(clause)) !== null) {
      const id = m[1];
      if (!whitelist.includes(id)) continue;
      const label = labelBeforeEqual(clause, m.index);
      if (label) result[id] = label;
    }
  }
  return result;
}

/** 取白名单 Map 的 key 数组（无则空数组） */
export function assetKeys(assetMap: Record<string, string> | null | undefined): string[] {
  return assetMap ? Object.keys(assetMap) : [];
}

/** 取资产 id 的中文含义（无则返回空串） */
export function assetLabel(assetMap: Record<string, string> | null | undefined, id: string): string {
  return assetMap?.[id] ?? "";
}
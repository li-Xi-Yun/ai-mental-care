from docx import Document
from docx.shared import Pt, Cm, RGBColor, Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn, nsdecls
from docx.oxml import parse_xml

doc = Document()

style = doc.styles['Normal']
style.font.name = 'Microsoft YaHei'
style.font.size = Pt(8)
style.element.rPr.rFonts.set(qn('w:eastAsia'), 'Microsoft YaHei')
style.paragraph_format.space_after = Pt(0)
style.paragraph_format.space_before = Pt(0)
style.paragraph_format.line_spacing = 1.0

FONT_BODY = 8
FONT_SECTION = 10
FONT_SUB = 8.5
FONT_TITLE = 16

for section in doc.sections:
    section.top_margin = Cm(1.0)
    section.bottom_margin = Cm(0.8)
    section.left_margin = Cm(1.5)
    section.right_margin = Cm(1.5)

def set_cell_shading(cell, color):
    shading_elm = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{color}"/>')
    cell._tc.get_or_add_tcPr().append(shading_elm)

def set_run(run, size=FONT_BODY, bold=False, color=None):
    run.bold = bold
    run.font.size = Pt(size)
    run.font.name = 'Microsoft YaHei'
    run.element.rPr.rFonts.set(qn('w:eastAsia'), 'Microsoft YaHei')
    if color:
        run.font.color.rgb = color

def add_section_title(text):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    run = p.add_run(text)
    set_run(run, FONT_SECTION, True, RGBColor(0x1A, 0x56, 0xDB))
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(1)
    border_xml = f'<w:pBdr {nsdecls("w")}><w:bottom w:val="single" w:sz="4" w:space="1" w:color="1A56DB"/></w:pBdr>'
    p._p.get_or_add_pPr().append(parse_xml(border_xml))

def add_sub_title(text):
    p = doc.add_paragraph()
    run = p.add_run(text)
    set_run(run, FONT_SUB, True, RGBColor(0x33, 0x33, 0x33))
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(0.5)

def add_body(text, bold=False, indent=False):
    p = doc.add_paragraph()
    if indent:
        p.paragraph_format.left_indent = Cm(0.3)
    run = p.add_run(text)
    set_run(run, FONT_BODY, bold)
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.space_before = Pt(0)
    return p

def add_bullet(text, level=0):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.4 + level * 0.3)
    prefix = "• " if level == 0 else "◦ "
    run = p.add_run(prefix + text)
    set_run(run, FONT_BODY)
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.space_before = Pt(0)
    return p

def add_inline_info(p, items):
    first = True
    for label, value in items:
        if not first:
            run = p.add_run("    ")
            set_run(run, FONT_BODY)
        run = p.add_run(f"{label}：")
        set_run(run, FONT_BODY, True)
        run2 = p.add_run(value if value else "________")
        set_run(run2, FONT_BODY)
        first = False

def add_info_table_compact(rows_data, cols=4):
    pairs = []
    for label, value in rows_data:
        pairs.append((label, value if value else ""))
    table = doc.add_table(rows=(len(pairs) + cols // 2 - 1) // (cols // 2), cols=cols)
    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    for i, (label, value) in enumerate(pairs):
        row_idx = i // (cols // 2)
        col_offset = (i % (cols // 2)) * 2
        cell_label = table.cell(row_idx, col_offset)
        cell_value = table.cell(row_idx, col_offset + 1)
        cell_label.text = label
        cell_value.text = value
        for cell in [cell_label, cell_value]:
            for paragraph in cell.paragraphs:
                paragraph.paragraph_format.space_after = Pt(0)
                paragraph.paragraph_format.space_before = Pt(0)
                paragraph.paragraph_format.line_spacing = 1.0
                for run in paragraph.runs:
                    set_run(run, FONT_BODY)
        for paragraph in cell_label.paragraphs:
            for run in paragraph.runs:
                run.bold = True
        set_cell_shading(cell_label, "F0F4FF")
    for row in table.rows:
        for i, cell in enumerate(row.cells):
            if i % 2 == 0:
                cell.width = Inches(1.0)
            else:
                cell.width = Inches(2.3)

# ========== 标题 ==========
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
run = p.add_run("个 人 简 历")
set_run(run, FONT_TITLE, True, RGBColor(0x1A, 0x56, 0xDB))
p.paragraph_format.space_after = Pt(2)

# ========== 基本信息 ==========
add_section_title("基本信息")
add_info_table_compact([
    ("姓名", ""), ("性别", ""), ("出生年月", ""),
    ("手机", ""), ("邮箱", ""), ("求职意向", "Java后端开发实习生"),
    ("期望城市", ""), ("到岗时间", ""), ("实习时长", ""),
], cols=6)

# ========== 教育背景 ==========
add_section_title("教育背景")
add_info_table_compact([
    ("学校", ""), ("学历", ""), ("专业", ""),
    ("入学时间", ""), ("毕业时间", ""), ("GPA/排名", ""),
], cols=6)

# ========== 技术技能 ==========
add_section_title("技术技能")

p = doc.add_paragraph()
p.paragraph_format.left_indent = Cm(0.3)
items = [
    ("编程语言", "Java 17（熟练）、SQL（熟练）"),
    ("框架", "Spring Boot 3.x、Spring Security、Spring AI、Spring WebSocket、MyBatis-Plus、PageHelper、Dynamic DataSource"),
    ("缓存", "Redis、Redisson、Lock4j"),
    ("AI", "Spring AI Alibaba（DashScope）、Spring AI DeepSeek、Spring AI Ollama、Alibaba Cloud AI Graph（StateGraph 工作流引擎）"),
    ("向量库", "Milvus（向量存储与检索）、Spring AI Vector Store"),
    ("数据库", "MySQL 8.0（索引优化、事务管理）"),
    ("接口", "RESTful API、SpringDoc OpenAPI / Knife4j"),
    ("工具", "Hutool、Lombok、MapStruct-Plus、OkHttp、HttpClient 5、POI、EasyExcel"),
    ("云服务", "AWS S3、腾讯云 COS、阿里云/腾讯云 SMS、DashScope ASR&TTS、OAuth2 GitHub"),
    ("工程化", "Maven 多模块、Git、Logback、P6Spy"),
    ("架构", "装饰器、工厂、钩子/拦截器、StateGraph 工作流、Reactor Flux 响应式、CompletableFuture 异步、ForkJoinPool 并行"),
]
for label, value in items:
    run = p.add_run(f"{label}：")
    set_run(run, FONT_BODY, True)
    run2 = p.add_run(value + "  ")
    set_run(run2, FONT_BODY)
    if label in ("框架", "AI", "云服务", "架构"):
        run2_add = p.add_run("\n")
        set_run(run2_add, FONT_BODY)

# ========== 项目经历 ==========
add_section_title("项目经历")

add_sub_title("AI 心理健康关怀系统（ai-mental-care）")
p = doc.add_paragraph()
p.paragraph_format.left_indent = Cm(0.3)
run = p.add_run("时间：2026.03–至今    角色：独立开发    技术栈：Spring Boot 3.5 / Spring AI / AI Graph / MyBatis-Plus / Redis / Redisson / Milvus / MySQL / WebSocket / DashScope ASR&TTS / JWT / OAuth2")
set_run(run, FONT_BODY)

add_body("项目描述：基于 AI 大模型的心理健康关怀平台，整合多模型对话、多阶段心理诊断工作流、RAG 检索增强生成、实时语音交互、心理量表测评等核心能力，为用户提供智能化的心理健康评估与干预建议服务。", indent=True)

add_body("核心模块：", bold=True, indent=True)

add_bullet("AI 心理诊断工作流引擎：基于 StateGraph 构建四阶段诊断流水线（输入侧→知识侧→处理侧→持久化）；输入侧三节点 ForkJoinPool 并行（预清洗、结构化、情绪统计、归一化）；知识侧查询变换→并行检索（症状/诊断标准/干预方案）→重排，条件边重试机制；处理侧六节点并行（情绪综合分析、病程归因、心理状态评估、社会功能影响、保护性因素、风险评估）→干预建议→总结；自定义 StateSerializer 支持断点恢复", 0)

add_bullet("RAG 检索增强生成：构建 RAG 工作流图（查询压缩+重写并行→多查询扩展→Milvus 向量检索→重排序→文档拼接），条件边重试与中断机制，集成 Milvus 支持 PDF/Markdown/Tika 多格式 Embedding 与检索", 0)

add_bullet("实时语音对话：集成 DashScope ASR（paraformer-realtime-8k-v2）实时语音识别+中间结果弹幕推送；DashScope TTS（CosyVoice v3）语音合成+WebSocket 流式推送音频二进制数据；ASR→文本处理→TTS 完整链路", 0)

add_bullet("多模型对话与流式处理：ChatModelFactory 工厂模式支持 DeepSeek/DashScope/Ollama 三模型切换；Reactor Flux 流式管道+AgentStreamProcessor 装饰器链（思考聚合→日志→持久化→监听分发）；Agent Hook/Interceptor 拦截增强", 0)

add_bullet("心理量表测评：量表分类/题目/选项模板/评分规则完整数据模型，用户答题、自动评分、结果规则匹配", 0)

add_bullet("通用基础架构（16 个 Common 模块）：JWT+OAuth2 GitHub+Security 权限；Redis+Lua 分布式限流；Redisson+Lock4j 分布式锁；AWS S3+腾讯云 COS 对象存储；邮件/短信/WebSocket 实时推送；脱敏/翻译/校验/验证码/JSON 序列化", 0)

add_body("亮点：", bold=True, indent=True)
add_bullet("StateGraph 工作流编排复杂 AI 诊断流程（并行节点+条件边路由+中断重试）；装饰器模式流式管道灵活叠加能力；三模型无缝切换；16 模块模块化设计实现基础能力复用与解耦", 0)

# ========== 专业能力总结 ==========
add_section_title("专业能力总结")
p = doc.add_paragraph()
p.paragraph_format.left_indent = Cm(0.3)
skills = [
    "熟悉 Java 17，掌握 Spring Boot 3.x 全栈开发，具备独立从零搭建项目能力",
    "深入理解 Spring AI 与 AI Graph 工作流引擎，具备 AI Agent 应用开发经验",
    "掌握 RAG 全流程（文档解析→Embedding→向量存储→检索→重排序）",
    "熟悉 MySQL 设计优化，掌握 Redis 缓存策略与分布式锁",
    "具备 WebSocket 实时通信与 Reactor Flux 流式数据处理经验",
    "熟悉装饰器、工厂、钩子/拦截器、状态图等设计模式实战应用",
    "具备多模块 Maven 项目架构能力，注重代码分层与模块解耦",
]
for i, s in enumerate(skills):
    run = p.add_run(f"• {s}")
    set_run(run, FONT_BODY)
    if i < len(skills) - 1:
        run2 = p.add_run("  ")
        set_run(run2, FONT_BODY)

# ========== 自我评价 ==========
add_section_title("自我评价")
p = doc.add_paragraph()
p.paragraph_format.left_indent = Cm(0.3)
evals = [
    "对 AI 应用开发有浓厚兴趣，持续关注 Spring AI、LangChain 等框架发展",
    "独立开发能力强，从需求分析到架构设计到编码实现全流程独立完成",
    "注重代码质量与工程规范，善用设计模式解决复杂业务场景",
    "学习能力强，能快速掌握新技术并应用到实际项目",
]
for i, s in enumerate(evals):
    run = p.add_run(f"• {s}")
    set_run(run, FONT_BODY)
    if i < len(evals) - 1:
        run2 = p.add_run("  ")
        set_run(run2, FONT_BODY)

output_path = r"D:\JavaProject\ai_mental_care\brief\resume.docx"
doc.save(output_path)
print(f"简历已保存到: {output_path}")
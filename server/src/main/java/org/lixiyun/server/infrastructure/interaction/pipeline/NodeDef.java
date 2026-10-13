package org.lixiyun.server.infrastructure.interaction.pipeline;

import java.util.function.Supplier;

/**
 * 输出节点注册表项——声明式装配的"一张表"
 *
 * <p>{@link OutputPipelineFactory} 依据该表完成通用装配（filter + sort + 链接），
 * 新增输出类型只需新增一条 {@code NodeDef}，Factory 与既有节点零改动。</p>
 *
 * <h3>字段语义：</h3>
 * <ul>
 *   <li><b>name</b>：节点名称，仅作日志/排错标识</li>
 *   <li><b>servesType</b>：本节点负责的输出类型；{@code null} 表示任何输出组合都必须存在（源节点，如模型输出）</li>
 *   <li><b>order</b>：链内排序（消费顺序），数值越小越靠前</li>
 *   <li><b>supplier</b>：每会话创建独立节点实例的工厂（对应 `@Scope("prototype")`，由
 *       {@code ObjectProvider::getObject} 提供）</li>
 * </ul>
 *
 * @param name       节点名称
 * @param servesType 服务类型；{@code null}=源节点恒在
 * @param order      链内排序（升序）
 * @param supplier   节点实例工厂（每会话新建）
 */
public record NodeDef(String name, OutputDataType servesType, int order, Supplier<OutputNode> supplier) {
}
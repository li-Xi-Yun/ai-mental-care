package org.lixiyun.server.controller.user.conversation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.user.conversation.ConversationLifecycleInitDTO;
import org.lixiyun.pojo.vo.user.conversation.ConversationLifecycleInitVO;
import org.lixiyun.server.service.user.ConversationLifecycleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 对话生命周期控制器
 *
 * <p>控制一整个对话的生命周期，包括会话创建时资源初始化、会话销毁处理。
 * 所有业务逻辑委托给 {@link ConversationLifecycleService} 处理。</p>
 *
 * <h3>职责边界</h3>
 * <ul>
 *   <li><b>initSession</b>：根据输入/输出类型初始化适配器和管道，返回端点路径</li>
 *   <li><b>endSession</b>：销毁适配器和管道，释放所有资源</li>
 *   <li>interrupt（中断）由各 Adapter 内部根据实时数据自动触发，不暴露为独立端点</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-10-02
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/conversation/lifecycle")
@Tag(name = "对话生命周期管理", description = "对话的初始化、销毁等生命周期管理接口")
public class ConversationLifecycleController {

    private final ConversationLifecycleService conversationLifecycleService;

    @PostMapping("/init")
    @Operation(summary = "初始化对话生命周期", description = """
            根据输入/输出类型初始化适配器和处理管道，返回各节点Socket端点路径。
            客户端依据返回的路径连接对应WebSocket端点进行数据收发。
            - inputTypes：输入数据类型（TEXT、AUDIO等）
            - outputTypes：输出数据类型（TEXT、AUDIO等）
            """)
    public Result<ConversationLifecycleInitVO> initSession(
            @RequestBody @Valid ConversationLifecycleInitDTO request
    ) {
        log.info("[对话生命周期] 收到初始化请求，会话ID：{}", request.getConversationId());
        ConversationLifecycleInitVO vo = conversationLifecycleService.initSession(request);
        return Result.success(vo);
    }

    @DeleteMapping("/{conversationId}")
    @Operation(summary = "销毁对话生命周期", description = "销毁指定会话的输入适配器和输出管道，释放所有资源")
    public Result<Void> endSession(
            @PathVariable
            @Parameter(description = "会话ID", required = true)
            @NotNull(message = "会话ID不能为空")
            Long conversationId
    ) {
        log.info("[对话生命周期] 收到销毁请求，会话ID：{}", conversationId);
        conversationLifecycleService.endSession(conversationId);
        return Result.success();
    }
}
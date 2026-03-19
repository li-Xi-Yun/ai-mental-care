package org.lixiyun.server.controller.conversation;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.server.service.DialogueService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author lixiyun
 * @since 2026-03-19 20:24
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/conversaion/dialogue")
@Tag(name = "对话相关接口", description = "对话相关接口")
public class DialogueController {

    private final DialogueService dialogueService;

}

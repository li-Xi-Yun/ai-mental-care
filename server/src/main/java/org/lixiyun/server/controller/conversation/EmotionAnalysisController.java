package org.lixiyun.server.controller.conversation;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.server.service.EmotionAnalysisService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author lixiyun
 * @since 2026-03-19 20:25
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/conversaion/emotion-analysis")
@Tag(name = "情绪分析相关接口", description = "情绪分析相关接口")
public class EmotionAnalysisController {

    private final EmotionAnalysisService emotionAnalysisService;


}

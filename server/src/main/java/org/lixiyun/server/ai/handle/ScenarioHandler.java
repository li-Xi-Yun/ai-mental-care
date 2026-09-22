package org.lixiyun.server.ai.handle;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.prompt.constant.ScenarioConstant;
import org.lixiyun.common.agent.prompt.utils.PromptUtil;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author lixiyun
 * @since 2026-04-21 15:42
 */
@Slf4j
@Component
@DependsOn("promptUtil")
@Deprecated
public class ScenarioHandler {

    // key：scenario(对话场景类型：0-日常闲聊，1-情绪纾解、2-助眠陪伴、3-考前减压、4-职场调适、5-亲密沟通、6-自信赋能)，
    // value：对应场景的模型提示词
    private static final ConcurrentHashMap<Integer, String> SCENARIO_MAP = new ConcurrentHashMap<>();

//    @PostConstruct
    public void init() {
        log.info("场景对话提示词加载");
        String professionalEmotionalCompanion = PromptUtil.getPrompt(ScenarioConstant.PROFESSIONAL_EMOTIONAL_COMPANION);
        String emotionRelief = PromptUtil.getPrompt(ScenarioConstant.EMOTIONAL_RELIEF);
        String sleepAidCompanion = PromptUtil.getPrompt(ScenarioConstant.SLEEP_AID_COMPANIONSHIP);
        String preExamStressRelief = PromptUtil.getPrompt(ScenarioConstant.PRE_EXAM_STRESS_RELIEF);
        String workplaceAdjustment = PromptUtil.getPrompt(ScenarioConstant.WORKPLACE_ADJUSTMENT);
        String intimateCommunication = PromptUtil.getPrompt(ScenarioConstant.INTIMATE_COMMUNICATION);
        String confidenceEmpowerment = PromptUtil.getPrompt(ScenarioConstant.CONFIDENCE_EMPOWERMENT);
        if(!professionalEmotionalCompanion.isEmpty()){
            SCENARIO_MAP.put(0, professionalEmotionalCompanion);
        }
        if(!emotionRelief.isEmpty()){
            SCENARIO_MAP.put(1, emotionRelief);
        }
        if(!sleepAidCompanion.isEmpty()){
            SCENARIO_MAP.put(2, sleepAidCompanion);
        }
        if(!preExamStressRelief.isEmpty()){
            SCENARIO_MAP.put(3, preExamStressRelief);
        }
        if(!workplaceAdjustment.isEmpty()){
            SCENARIO_MAP.put(4, workplaceAdjustment);
        }
        if(!intimateCommunication.isEmpty()){
            SCENARIO_MAP.put(5, intimateCommunication);
        }
        if(!confidenceEmpowerment.isEmpty()){
            SCENARIO_MAP.put(6, confidenceEmpowerment);
        }
        log.info("场景对话提示词加载完成-加载数量：{}", SCENARIO_MAP.size());
    }

    /**
     * 根据场景获取模型提示词
     *
     * @param scenario 场景类型
     * @return 模型提示词
     */
    public static String getScenarioPrompt(Integer scenario) {
        return SCENARIO_MAP.get(scenario);
    }


}
package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.lixiyun.pojo.entity.conversation.EmotionDiagnosis;
import org.lixiyun.pojo.vo.user.conversation.DiagnosisConversationVO;

import java.util.List;

/**
 * 情感诊断书表（多轮会话汇总）(EmotionDiagnosis) 表数据库访问层
 *
 * @author lixiyun
 * @since 2026-03-15
 */
public interface EmotionDiagnosisMapper extends BaseMapper<EmotionDiagnosis> {

    /**
     * 查询用户拥有诊断数据的会话列表（支持会话名称模糊匹配）
     *
     * @param userId          用户 ID
     * @param conversationName 会话名称（模糊匹配，可为空）
     * @return 拥有诊断数据的会话列表 {@link DiagnosisConversationVO}
     */
    List<DiagnosisConversationVO> selectDiagnosisConversationPage(@Param("userId") Long userId,
                                                                   @Param("conversationName") String conversationName);

}
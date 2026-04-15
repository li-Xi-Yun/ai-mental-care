package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.lixiyun.pojo.entity.conversation.GraphCheckpoint;

import java.util.List;

/**
 * 检查点数据表 (GraphCheckpoint) 表数据库访问层
 *
 * @author lixiyun
 * @since 2026-03-15
 */
public interface GraphCheckpointMapper extends BaseMapper<GraphCheckpoint> {

    List<GraphCheckpoint> loadCheckpointList(String conversationId);

    GraphCheckpoint loadCheckpoint(@Param("conversationId") String conversationId, @Param("currentRound") Integer currentRound);
}

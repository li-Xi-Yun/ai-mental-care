package org.lixiyun.server.saver;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.SpringUtils;
import org.lixiyun.pojo.entity.Conversation;
import org.lixiyun.pojo.entity.GraphCheckpoint;
import org.lixiyun.server.constant.GraphConstant;
import org.lixiyun.server.infrastructure.storage.ConversationHistoryMessagesStorage;
import org.lixiyun.server.mapper.ConversationMapper;
import org.lixiyun.server.mapper.GraphCheckpointMapper;
import org.springframework.ai.chat.messages.Message;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author lixiyun
 * @since 2026-03-15 13:51
 */
@Slf4j
public class CustomMysqlSaver extends MemorySaver {

    private final ConversationMapper conversationMapper = SpringUtils.getBean(ConversationMapper.class);

    private final GraphCheckpointMapper graphCheckpointMapper = SpringUtils.getBean(GraphCheckpointMapper.class);

    private final ConversationHistoryMessagesStorage conversationHistoryMessagesStorage = SpringUtils.getBean(ConversationHistoryMessagesStorage.class);

    private final TransactionTemplate transactionTemplate = SpringUtils.getBean(TransactionTemplate.class);

    // 序列化器（默认使用Spring AI Alibaba的默认序列化器，也可通过setter自定义）
    private final StateSerializer stateSerializer = com.alibaba.cloud.ai.graph.StateGraph.DEFAULT_JACKSON_SERIALIZER;

    public static Builder builder() {
        return new Builder();
    }

    private String encodeState(Map<String, Object> data) throws IOException {
        var binaryData = stateSerializer.dataToBytes(data);
        var base64Data = Base64.getEncoder().encodeToString(binaryData);
        return format("""
				{"binaryPayload": "%s"}
				""", base64Data);
    }

    private Map<String, Object> decodeState(String binaryPayload) throws IOException, ClassNotFoundException {
        byte[] bytes = Base64.getDecoder().decode(binaryPayload);
        return stateSerializer.dataFromBytes(bytes);
    }

    /**
     * 如果检查点列表为空，则从数据库加载检查点。<br>
     * 这个是会话开始时，最先使用的方法
     *
     * @param config     配置
     * @param checkpoints 检查点列表
     * @return 检查点列表
     * @throws Exception 如果在从数据库加载检查点时发生错误。
     */
    @Override
    protected LinkedList<Checkpoint> loadedCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints)
            throws Exception {
        if (!checkpoints.isEmpty()) {
            return checkpoints;
        }
        String conversationId  = config.threadId().orElse(THREAD_ID_DEFAULT);

        if(conversationId.equals(THREAD_ID_DEFAULT)){
            // 说明是该会话的第一次对话，创建对应的会话表数据
            Optional<Object> currentIdOpl = config.metadata(GraphConstant.USER_ID);
            Long currentId = (Long) currentIdOpl.orElseThrow(() -> new BusinessException(AuthenticationExceptionEnum.USER_NOT_LOGIN));
            Conversation conversation = Conversation.builder()
                    .userId(currentId)
                    .build();
            conversationMapper.insert(conversation);
            Field nameField = config.getClass().getDeclaredField("threadId");
            // 关闭访问权限检查
            nameField.setAccessible(true);
            // 为user实例的name属性赋值
            conversationId = conversation.getId().toString();
            nameField.set(config, conversationId);

            config.context().put(GraphConstant.CONVERSATION_FIRST, true);
        }

        try {
            // 转换为Checkpoint对象（适配原逻辑）
            List<GraphCheckpoint> graphCheckpointList = graphCheckpointMapper.loadCheckpointList(conversationId);
            for (GraphCheckpoint dbCheckpoint : graphCheckpointList) {
                Checkpoint checkpoint = Checkpoint.builder()
                        .id(dbCheckpoint.getCheckpointId())
                        .nodeId(dbCheckpoint.getNodeId())
                        .nextNodeId(dbCheckpoint.getNextNodeId())
                        // 反序列化stateData（原二进制数据）
                        .state(decodeState((String) dbCheckpoint.getStateData()))
                        .build();
                checkpoints.add(checkpoint);
            }
        } catch (Exception e) {
            log.error("加载检查点失败，会话ID：{}", conversationId, e);
            throw new Exception("Unable to load checkpoints", e);
        }
        return checkpoints;
    }

    /**
     * Inserts a checkpoint to the database
     *
     * @param config      the configuration
     * @param checkpoints the list of checkpoints
     * @param checkpoint  the checkpoint to insert
     * @throws Exception if an error occurs while inserting the checkpoint in the
     *                   database.
     */
    @Override
    @Transactional
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint)
            throws Exception {
        requireNonNull(checkpoint, "checkpoint cannot be null");
        String conversationId = config.threadId().orElse(THREAD_ID_DEFAULT);

        // 获取当前轮数
        Optional<Object> currentRoundOpl = config.metadata(Conversation.CURRENT_ROUND);
        int currentRound = (int) currentRoundOpl.orElseThrow(() -> new BusinessException(ConversationExceptionEnum.CONVERSATION_PARAM_ERROR));

        try {
            // 构建自定义GraphCheckpoint实体（映射原检查点数据）
            GraphCheckpoint graphCheckpoint = GraphCheckpoint.builder()
                    .checkpointId(checkpoint.getId() == null ? UUID.randomUUID().toString() : checkpoint.getId())
                    .conversationId(conversationId)
                    .nodeId(checkpoint.getNodeId())
                    .nextNodeId(checkpoint.getNextNodeId())
                    .stateData(encodeState(checkpoint.getState())) // 序列化state数据
                    .roundNum(currentRound)
                    .build();

            transactionTemplate.execute(status -> {
                // 插入检查点
                graphCheckpointMapper.insert(graphCheckpoint);
                log.debug("检查点{}插入成功，会话ID：{}", checkpoint.getId(), conversationId);

                // 保存会话历史消息
                Optional<Map<String, Object>> metadata = config.metadata();
                Map<String, Object> map = metadata.orElseThrow(() -> new BusinessException(ConversationExceptionEnum.CONVERSATION_METADATA_NOT_CONFIGURED));
                ArrayList<Message> conversationMessageList = (ArrayList<Message>) map.get(GraphConstant.CONVERSATION_MESSAGES);
                if(conversationMessageList == null){
                    throw new BusinessException(ConversationExceptionEnum.CONVERSATION_METADATA_NOT_CONFIGURED);
                }
                conversationHistoryMessagesStorage.save(Long.parseLong(conversationId), currentRound, conversationMessageList);
                conversationMessageList.clear();

                return null;
            });


        } catch (IOException | RuntimeException e) {
            log.error("插入检查点失败，检查点ID：{}，会话ID：{}", checkpoint.getId(), conversationId, e);
            throw new Exception("Unable to insert checkpoint", e);
        }

    }

    /**
     * Marks the checkpoints as released
     *
     * @param config      the configuration
     * @param checkpoints the checkpoints
     * @param releaseTag  the release tag
     * @throws Exception if an error occurs while marking the checkpoints as
     *                   released
     */
    @Override
    protected void releasedCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Tag releaseTag)
            throws Exception {

        String conversationId = config.threadId().orElse(THREAD_ID_DEFAULT);

        // 标记会话为删除（模拟"释放"逻辑，实际业务可自定义状态字段）
        LambdaUpdateWrapper<Conversation> updateWrapper = new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, Long.parseLong(conversationId))
                .eq(Conversation::getDeleted, 0)
                .set(Conversation::getDeleted, 1); // 标记为删除

        int rowsAffected = conversationMapper.update(null, updateWrapper);
        if (rowsAffected == 0) {
            throw new IllegalStateException(format("会话'%s'不存在或已释放", conversationId));
        }

        log.debug("会话{}释放成功", conversationId);
    }

    /**
     * If the checkpoint exists, updates the checkpoint, otherwise it inserts it.
     *
     * @param config      the configuration
     * @param checkpoints the list of checkpoints
     * @param checkpoint  the checkpoint
     * @throws Exception if an error occurs while inserting or updating the
     *                   checkpoint.
     */
    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint)
            throws Exception {
        requireNonNull(checkpoint, "checkpoint cannot be null");
        String conversationId = config.threadId().orElse(THREAD_ID_DEFAULT);

        try {
            // 1. 判断是否更新现有检查点（原config.checkPointId()逻辑）
            if (config.checkPointId().isPresent()) {
                // 更新逻辑：根据checkpointId更新
                LambdaUpdateWrapper<GraphCheckpoint> updateWrapper = new LambdaUpdateWrapper<GraphCheckpoint>()
                        .eq(GraphCheckpoint::getCheckpointId, config.checkPointId().get())
                        .eq(GraphCheckpoint::getDeleted, 0)
                        .set(GraphCheckpoint::getNodeId, checkpoint.getNodeId())
                        .set(GraphCheckpoint::getNextNodeId, checkpoint.getNextNodeId())
                        .set(GraphCheckpoint::getStateData, encodeState(checkpoint.getState()));

                int rowsAffected = graphCheckpointMapper.update(null, updateWrapper);
                if (rowsAffected == 0) {
                    throw new IllegalStateException(format("检查点%s不存在", config.checkPointId().get()));
                }
            } else {
                // 插入新检查点（复用insertedCheckpoint逻辑）
                insertedCheckpoint(config, checkpoints, checkpoint);
            }

            log.debug("检查点{}更新成功，会话ID：{}", checkpoint.getId(), conversationId);
        } catch (IOException | RuntimeException e) {
            log.error("更新检查点失败，检查点ID：{}，会话ID：{}", checkpoint.getId(), conversationId, e);
            throw new Exception("Unable to update checkpoint", e);
        }

    }


    public static class Builder extends MemorySaver.Builder {
        public CustomMysqlSaver build() {
            return new CustomMysqlSaver();
        }
    }

}

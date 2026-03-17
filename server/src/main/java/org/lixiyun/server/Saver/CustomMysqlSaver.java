package org.lixiyun.server.Saver;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.ConversationException;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.entity.Conversation;
import org.lixiyun.pojo.entity.GraphCheckpoint;
import org.lixiyun.server.infrastructure.storage.ConversationHistoryMessagesStorage;
import org.lixiyun.server.mapper.ConversationMapper;
import org.lixiyun.server.mapper.GraphCheckpointMapper;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author lixiyun
 * @since 2026-03-15 13:51
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomMysqlSaver extends MemorySaver {

    private final ConversationMapper conversationMapper;

    private final GraphCheckpointMapper graphCheckpointMapper;

    private final ConversationHistoryMessagesStorage conversationHistoryMessagesStorage;

    // 序列化器（默认使用Spring AI Alibaba的默认序列化器，也可通过setter自定义）
    private final StateSerializer stateSerializer = com.alibaba.cloud.ai.graph.StateGraph.DEFAULT_JACKSON_SERIALIZER;

    @PostConstruct
    private void init(){
        try {
            // 获取父类的私有_lock字段
            Field lockField = MemorySaver.class.getDeclaredField("_lock");
            lockField.setAccessible(true); // 突破私有访问限制

            ReentrantLock parentLock = (ReentrantLock) lockField.get(this);
            // 如果父类的_lock为null，初始化它
            if (parentLock == null) {
                parentLock = new ReentrantLock();
                lockField.set(this, parentLock); // 给父类的_lock赋值
            }
        } catch (Exception e) {
            log.error("修复父类锁失败", e);
        }
    }

    private String encodeState(Map<String, Object> data) throws IOException {
        var binaryData = stateSerializer.dataToBytes(data);
        var base64Data = Base64.getEncoder().encodeToString(binaryData);
//        return format("""
//				{"binaryPayload": "%s"}
//				""", base64Data);
        return base64Data;
    }

    private Map<String, Object> decodeState(byte[] binaryPayload) throws IOException, ClassNotFoundException {
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
            Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
            Conversation conversation = Conversation.builder()
                    .userId(currentId).build();
            conversationMapper.insert(conversation);
            Field nameField = config.getClass().getDeclaredField("threadId");
            // 关闭访问权限检查
            nameField.setAccessible(true);
            // 为user实例的name属性赋值
            nameField.set(config, conversation.getId());
            config.context().put(Conversation.CURRENT_ROUND, conversation.getCurrentRound());
            return checkpoints;
        }
        // 传入的是上一次对话时的轮次，需要加1，表示当前轮次
//        config.context().put(Conversation.CURRENT_ROUND, (Integer) config.context().get(Conversation.CURRENT_ROUND) + 1);

        try {
            // 1. 查询指定会话的检查点（按创建时间倒序，同原逻辑）
            LambdaQueryWrapper<GraphCheckpoint> queryWrapper = new LambdaQueryWrapper<GraphCheckpoint>()
                    .eq(GraphCheckpoint::getConversationId, conversationId)
                    .eq(GraphCheckpoint::getDeleted, 0) // 未删除
                    .orderByDesc(GraphCheckpoint::getCreatedTime);

            // 2. 转换为Checkpoint对象（适配原逻辑）
            for (GraphCheckpoint dbCheckpoint : graphCheckpointMapper.selectList(queryWrapper)) {
                Checkpoint checkpoint = Checkpoint.builder()
                        .id(dbCheckpoint.getCheckpointId())
                        .nodeId(dbCheckpoint.getNodeId())
                        .nextNodeId(dbCheckpoint.getNextNodeId())
                        // 反序列化stateData（原二进制数据）
                        .state(decodeState((byte[]) dbCheckpoint.getStateData()))
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

        try {
            // 构建自定义GraphCheckpoint实体（映射原检查点数据）
            GraphCheckpoint graphCheckpoint = GraphCheckpoint.builder()
                    .checkpointId(checkpoint.getId() == null ? UUID.randomUUID().toString() : checkpoint.getId())
                    .conversationId(conversationId)
                    .nodeId(checkpoint.getNodeId())
                    .nextNodeId(checkpoint.getNextNodeId())
                    .stateData(encodeState(checkpoint.getState())) // 序列化state数据
                    .build();

            // 插入检查点
            graphCheckpointMapper.insert(graphCheckpoint);
            log.debug("检查点{}插入成功，会话ID：{}", checkpoint.getId(), conversationId);

            Optional<Object> currentRoundOpl = config.metadata(Conversation.CURRENT_ROUND);
            int currentRound = (int) currentRoundOpl.orElseThrow(() -> new BusinessException(ConversationException.CONVERSATION_PARAM_ERROR));

            List<Message> messages = (List<Message>) checkpoint.getState().get("messages");
            conversationHistoryMessagesStorage.save(Long.parseLong(conversationId), currentRound, messages.get(messages.size() - 1));


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

}

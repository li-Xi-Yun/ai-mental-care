package org.lixiyun.server.ai.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * @author lixiyun
 * @since 2026-01-22 11:15
 */
@Component(FreightServiceTool.NAME)
public class FreightServiceTool implements org.lixiyun.common.agent.skill.tools.Tools {

    public static final String NAME = "FreightServiceTool";

    public static final String EXIST_DRIVER_AVAILABLE = "existDriverAvailable";

    @Tool(name = EXIST_DRIVER_AVAILABLE,
        description = """
                查询现在是否有司机有空接单
                当客户提问“有车吗”或是“能运吗”等需要判断是否有司机能运货时，进行调用
                当本次用户提问之前已经调用过该工具时，则不用重复进行调用，除非上次调用该工具的时间过久或是会话过长，才重新调用
                返回true：有司机，返回false：没有司机
                """)
    private boolean existDriverAvailable(){
        return true;
    }
}

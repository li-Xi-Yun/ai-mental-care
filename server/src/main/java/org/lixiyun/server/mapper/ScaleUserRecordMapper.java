package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.lixiyun.pojo.entity.scale.ScaleUserRecord;

/**
 * 用户测评记录表(ScaleUserRecord)表数据库访问层
 *
 * @author lixiyun
 * @since 2026-04-15 08:24:17
 */
public interface ScaleUserRecordMapper extends BaseMapper<ScaleUserRecord> {

    /**
     * 插入一条记录（选择字段，策略插入）
     *
     * @param record 实体对象
     */
    @Options(useGeneratedKeys = true, keyProperty = "record.id", keyColumn = "id")
    @Insert("insert into ai_mental_care.scale_user_record(user_id, scale_id, scale_name) " +
            "VALUES(#{record.userId}, #{record.scaleId}, (select scale_name from ai_mental_care.scale where id = #{record.scaleId})) ")
    void insertOneWithScaleName(@Param("record") ScaleUserRecord record);
}

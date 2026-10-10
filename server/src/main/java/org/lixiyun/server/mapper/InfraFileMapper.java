package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.lixiyun.pojo.entity.file.InfraFile;

/**
 * 文件元数据表(InfraFile)表数据库访问层
 *
 * @author lixiyun
 * @since 2026-05-01 00:00:00
 */
public interface InfraFileMapper extends BaseMapper<InfraFile> {

    /**
     * 根据文件MD5查询文件元数据
     *
     * @param fileMd5 文件MD5
     * @return 文件元数据
     */
    @Select("select * from ai_mental_care.infra_file where file_md5 = #{fileMd5}")
    InfraFile selectMyFileByFileMd5(@Param("fileMd5") String fileMd5);

    /**
     * 恢复指定文件（绕过 @TableLogic 逻辑删除过滤）
     * <p>
     * 注意：{@code @TableLogic} 会自动给 wrapper 更新追加 {@code deleted = 0} 条件，
     * 用 wrapper 把 deleted 改回 0 会静默失效（匹配 0 行），必须用自定义 SQL。
     *
     * @param id 文件ID
     * @return 影响行数
     */
    @Update("UPDATE ai_mental_care.infra_file SET deleted = 0, updated_time = NOW() WHERE id = #{id}")
    int restoreFile(@Param("id") Long id);
}

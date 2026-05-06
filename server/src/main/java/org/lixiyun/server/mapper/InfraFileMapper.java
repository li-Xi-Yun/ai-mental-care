package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
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
}

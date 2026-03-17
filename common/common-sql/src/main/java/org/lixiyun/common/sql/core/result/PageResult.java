package org.lixiyun.common.sql.core.result;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 封装分页查询结果
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "分页查询结果")
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "总页数")
    private long total; //总记录数

    @Schema(description = "当前页数据")
    private List<T> records; //当前页数据集合

    /**
     * 封装分页查询结果
     *
     * @param page 分页数据
     * @param voClass VO类型，用于指定转换后的数据类型
     */
    public static <E, VO> PageResult<VO> convert(IPage<E> page, Class<VO> voClass) {
        return new PageResult<>(page.getTotal(), page.getRecords().stream().map(record -> BeanUtil.copyProperties(record, voClass)).toList());
    }

    /**
     * 封装分页查询结果
     * @param page 分页数据
     * @param function 转换函数
     * @return 转换后的分页数据
     */
    public static <E, VO> PageResult<VO> convert(IPage<E> page, Function<E, VO> function) {
        return new PageResult<>(page.getTotal(), page.getRecords().stream().map(function).toList());
    }

    /**
     * 封装分页查询结果
     * @param page 分页数据
     * @param function 筛选条件
     * @param voClass VO类型，用于指定转换后的数据类型
     */
    public static <E, VO> PageResult<VO> convert(IPage<E> page, Predicate<E> function, Class<VO> voClass) {
        return new PageResult<>(page.getTotal(), page.getRecords().stream()
                .filter(function).map(record -> BeanUtil.copyProperties(record, voClass)).toList());
    }



}

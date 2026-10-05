package org.lixiyun.pojo.entity.scale;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.pojo.constant.DeleteConstant;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 常模组定义表(scale_norm_group)实体类
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaleNormGroup implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 常模计算方式：公式法 T=50+10*(X-M)/SD */
    public static final int NORM_TYPE_FORMULA = 0;
    /** 常模计算方式：查表法 */
    public static final int NORM_TYPE_TABLE = 1;

    /** 常模组ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 量表版本ID */
    private Long scaleVersionId;

    /** 维度ID，NULL代表总分常模 */
    private Long dimensionId;

    /** 常模组名称，如"全国成年男性常模" */
    private String groupName;

    /** 常模组编码，方便程序查找 */
    private String groupCode;

    /** 性别：1=男 0=女 NULL=不限 */
    private Integer gender;

    /** 最小年龄（包含） */
    private Integer ageMin;

    /** 最大年龄（包含） */
    private Integer ageMax;

    /** 学历：1=初中, 2=高中, 3=大专, 4=本科, 5=硕士, 6=博士 NULL=不限 */
    private Integer education;

    /** 职业群体：学生/医护/军人/企业员工 NULL=不限 */
    private Integer occupation;

    /** 地区：华北/华东/华南/西南 NULL=不限 */
    private Integer region;

    /** 常模计算方式：0=公式法(T=50+10*(X-M)/SD) 1=查表法，应用层检查数据完整性 */
    private Integer normType;

    /** 原始分均值 M */
    private BigDecimal mean;

    /** 原始分标准差 SD */
    private BigDecimal sd;

    /** 常模来源/参考文献 */
    private String source;

    /** 常模制定年份 */
    private Integer normYear;

    /** 排序：默认常模排在最前 */
    private Integer sort;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 创建人用户ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 最后更新时间 */
    private LocalDateTime updatedTime;

    /** 最后更新人用户ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /** 是否删除，0=否 1=是 */
    @TableLogic
    private Integer deleted;
    /**
     * 判断常模组是否已删除
     *
     * @return true表示已删除，false表示未删除
     */
    public boolean deleteFlat() {
        return deleted != null && deleted == DeleteConstant.DELETE_FLAG_YES;
    }
}

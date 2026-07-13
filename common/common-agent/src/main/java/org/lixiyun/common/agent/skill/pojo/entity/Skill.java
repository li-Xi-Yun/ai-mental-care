package org.lixiyun.common.agent.skill.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-15 19:42
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Skill implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 技能名称
     */
    private String name;

    /**
     * 技能描述
     */
    private String description;

    /**
     * 技能版本
     */
    private String version;

    /**
     * 技能作者
     */
    private String author;

    /**
     * 技能标签，领域标签，小写英文
     */
    private List<String> tags;

    /**
     * 技能的相对URL, 例如：/skills/skill1/SKILL.md
     */
    private String url;
    
    /**
     * 索引文件标识，0-实际Skill文件，1-Skill索引文件
     */
    private Integer isIndex;
    
    /**
     * 删除标识，0-未删除，1-已删除
     */
    private Integer deleteFlag;

    public boolean equals(Skill other) {
        return name.equals(other.name) &&
                description.equals(other.description) &&
                version.equals(other.version) &&
                author.equals(other.author) &&
                tags.equals(other.tags) &&
                isIndex.equals(other.isIndex) &&
                deleteFlag.equals(other.deleteFlag);
    }

}
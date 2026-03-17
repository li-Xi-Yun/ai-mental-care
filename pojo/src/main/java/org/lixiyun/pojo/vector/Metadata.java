package org.lixiyun.pojo.vector;

import lombok.Builder;
import lombok.Data;

/**
 * @author lixiyun
 * @since 2026-01-06 16:48
 */
@Data
@Builder
public class Metadata implements java.io.Serializable{

    private static final long serialVersionUID = 1L;

    private String dataId;

}

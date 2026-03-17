package org.lixiyun.common.core.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.lixiyun.common.core.error.ErrorCode;

@Data
@Schema(description = "顶层的返回数据格式")
public class Result<T> {

    @Schema(description = "状态码", example = "200")
    private Integer code;

    @Schema(description = "返回信息", example = "成功")
    private String msg;

    @Schema(description = "返回数据")
    private T data;

    public Result() {
    }

    public static <T> Result<T> success() {
        return Result.success(null);
    }

    public static <T> Result<T> success(T data) {
        return Result.success(200, "成功", data);
    }

    public static <T> Result<T> success(Integer code, String msg, T data) {
        Result<T> result = new Result<>();
        result.code = code;
        result.msg = msg;
        result.data = data;
        return result;
    }

    public static <T> Result<T> error(ErrorCode e) {
        return Result.error(e.getCode(), e.getMsg());
    }

    public static <T> Result<T> error(Integer code, String msg) {
        Result<T> result = new Result<T>();
        result.code = code;
        result.msg = msg;
        return result;
    }

}

package com.odysseygen.common;


import lombok.Data;

@Data
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public Result(int code, String message, T data){
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success(T data){
        return new Result<>(200, "Success", data);
    }

    public static <T> Result<T> success(String message, T data){
        return new Result<>(200, message, data);
    }

    public static <T> Result<T> success(){
        return new Result<>(200, "Success", null);
    }

    public static <T> Result<T> error(String message)
    {
        return new Result<>(500, message, null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> of(Integer code, String message, T data) {
        return new Result<>(code, message, data);
    }
}

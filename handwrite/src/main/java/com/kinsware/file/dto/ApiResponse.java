package com.kinsware.file.dto;

import lombok.Data;

@Data
public class ApiResponse<T> {
    private T data;
    private String message;
    private Integer code;
    
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setData(data);
        response.setMessage("成功");
        response.setCode(0);
        return response;
    }
    
    public static <T> ApiResponse<T> error(String message, Integer code) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setMessage(message);
        response.setCode(code);
        return response;
    }
}

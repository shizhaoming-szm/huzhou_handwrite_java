package com.kinsware.file.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
@ApiModel(description = "混合签名验证请求")
public class MixRequest {
    
    @ApiModelProperty(value = "图片文件列表")
    private List<byte[]> images;
    
    @ApiModelProperty(value = "Qwen服务器地址", example = "http://192.168.111.86:11434/v1")
    private String qwenServer;
    
    @ApiModelProperty(value = "Qwen模型名称", example = "qwen3-vl:8b-instruct")
    private String qwenModel;
    
    @ApiModelProperty(value = "Qwen API密钥", example = "not-needed")
    private String qwenApiKey;
    
    @ApiModelProperty(value = "ContextGem服务器地址", example = "http://192.168.111.86:11434/v1")
    private String cgServer;
    
    @ApiModelProperty(value = "ContextGem模型名称", example = "qwen3-vl:8b-instruct")
    private String cgModel;
    
    @ApiModelProperty(value = "ContextGem API密钥", example = "sk-155b32c6d3334255a3539a3839cc4d99")
    private String cgApiKey;
    
    @ApiModelProperty(value = "提示词", example = "这两张图分别为身份证与手写签名...")
    private String prompt;
    
    @ApiModelProperty(value = "最大tokens", example = "16384")
    private Integer maxTokens;
    
    @ApiModelProperty(value = "最大段落数", example = "2000")
    private Integer maxParas;
    
    @ApiModelProperty(value = "最大项目数", example = "5")
    private Integer maxItems;
}

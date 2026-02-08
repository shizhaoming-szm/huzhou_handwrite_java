package com.kinsware.file.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
@ApiModel(description = "图片分类请求")
public class ClassifyRequest {

	@ApiModelProperty(value = "图片文件列表")
	private List<byte[]> images;

	@ApiModelProperty(value = "Qwen服务器地址", example = "https://llm.huzhou.gov.cn/servingpod/b50538bd4c0a44ddb94666e7b3e756c9/v1/chat/completions")
	private String qwenServer;

	@ApiModelProperty(value = "Qwen模型名称", example = "hzrs-Qwen3-VL-8B-Instruct")
	private String qwenModel;

	@ApiModelProperty(value = "Qwen API密钥", example = "not-needed")
	private String qwenApiKey;

	@ApiModelProperty(value = "提示词", example = "对图片进行分类...")
	private String prompt;

	@ApiModelProperty(value = "最大tokens", example = "16384")
	private Integer maxTokens;

	@ApiModelProperty(value = "最大段落数", example = "2000")
	private Integer maxParas;

	@ApiModelProperty(value = "最大项目数", example = "5")
	private Integer maxItems;
}

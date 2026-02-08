package com.kinsware.file.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
@ApiModel(description = "混合签名验证响应")
public class MixResponse {

	@JsonProperty("qwen_text")
	@ApiModelProperty(value = "视觉识别文本")
	private String qwenText;

	@ApiModelProperty(value = "处理时长(秒)")
	private Double duration;

	@ApiModelProperty(value = "抽取结果")
	private Concepts concepts;

	@ApiModelProperty(value = "原始JSON数据")
	private Object raw;

	@Data
	@ApiModel(description = "概念抽取结果")
	public static class Concepts {
		@ApiModelProperty(value = "姓名列表")
		private List<String> 姓名;

		@ApiModelProperty(value = "签名一致列表")
		private List<Boolean> 签名一致;
	}
}

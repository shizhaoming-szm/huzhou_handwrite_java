package com.kinsware.file.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "图片分类响应")
public class ClassifyResponse {

	@ApiModelProperty(value = "响应体")
	private ResponseBody responseBody;

	@ApiModelProperty(value = "消息", example = "成功")
	private String message;

	@ApiModelProperty(value = "错误码", example = "0")
	private Integer errorCode;

	@Data
	@ApiModel(description = "响应体")
	public static class ResponseBody {
		@ApiModelProperty(value = "LLM结果列表")
		private java.util.List<String> llmResult;

		@ApiModelProperty(value = "OCR结果")
		private String ocrResult;
	}
}

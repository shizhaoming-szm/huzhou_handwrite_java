package com.kinsware.file.controller;

import com.kinsware.file.dto.ClassifyResponse;
import com.kinsware.file.dto.MixResponse;
import com.kinsware.file.service.ImageClassificationService;
import com.kinsware.file.service.SignatureVerificationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@RestController
@Api(tags = "手写签名验证API")
public class HandwriteController {

	private static final Logger logger = LoggerFactory.getLogger(HandwriteController.class);

	private final SignatureVerificationService signatureService;
	private final ImageClassificationService classificationService;

	public HandwriteController(SignatureVerificationService signatureService,
			ImageClassificationService classificationService) {
		this.signatureService = signatureService;
		this.classificationService = classificationService;
	}

	@PostMapping(value = "/mix", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ApiOperation(value = "手写签名验证", notes = "对身份证和手写签名进行视觉识别和一致性验证")
	public MixResponse mix(HttpServletRequest request,
			@ApiParam(value = "Qwen服务器地址") @RequestParam(value = "qwen_server", required = false, defaultValue = "http://192.168.111.86:11434/v1") String qwenServer,
			@ApiParam(value = "Qwen模型名称") @RequestParam(value = "qwen_model", required = false, defaultValue = "qwen3-vl:8b-instruct") String qwenModel,
			@ApiParam(value = "Qwen API密钥") @RequestParam(value = "qwen_api_key", required = false, defaultValue = "not-needed") String qwenApiKey,
			@ApiParam(value = "ContextGem服务器地址") @RequestParam(value = "cg_server", required = false, defaultValue = "http://192.168.111.86:11434/v1") String cgServer,
			@ApiParam(value = "ContextGem模型名称") @RequestParam(value = "cg_model", required = false, defaultValue = "qwen3-vl:8b-instruct") String cgModel,
			@ApiParam(value = "ContextGem API密钥") @RequestParam(value = "cg_api_key", required = false, defaultValue = "sk-155b32c6d3334255a3539a3839cc4d99") String cgApiKey,
			@ApiParam(value = "提示词") @RequestParam(value = "prompt", required = false, defaultValue = "这两张图分别为身份证与手写签名。请先准确识别并输出两图的文本内容，尤其是身份证上的姓名原文和手写签名的逐字转写；不要编造。如签名过于模糊或潦草无法辨认请明确说明。依据规则：若签名可读内容与身份证姓名的相似度低于80%则判为不一致；若无法判断也视为不一致。") String prompt,
			@ApiParam(value = "最大tokens") @RequestParam(value = "max_tokens", required = false, defaultValue = "16384") Integer maxTokens,
			@ApiParam(value = "最大段落数") @RequestParam(value = "max_paras", required = false, defaultValue = "2000") Integer maxParas,
			@ApiParam(value = "最大项目数") @RequestParam(value = "max_items", required = false, defaultValue = "5") Integer maxItems) {

		List<MultipartFile> images = extractFiles(request, "images");
		if (images == null || images.isEmpty()) {
			throw new IllegalArgumentException("未提供图片");
		}
		logger.info("收到mix请求: images={}", images.size());
		return signatureService.verifySignature(images, qwenServer, qwenModel, qwenApiKey,
				cgServer, cgModel, cgApiKey, prompt, maxTokens);
	}

	@PostMapping(value = "/classify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ApiOperation(value = "图片分类", notes = "对图片进行分类识别")
	public ClassifyResponse classify(HttpServletRequest request,
			@ApiParam(value = "Qwen服务器地址") @RequestParam(value = "qwen_server", required = false, defaultValue = "https://llm.huzhou.gov.cn/servingpod/b50538bd4c0a44ddb94666e7b3e756c9/v1/chat/completions") String qwenServer,
			@ApiParam(value = "Qwen模型名称") @RequestParam(value = "qwen_model", required = false, defaultValue = "hzrs-Qwen3-VL-8B-Instruct") String qwenModel,
			@ApiParam(value = "Qwen API密钥") @RequestParam(value = "qwen_api_key", required = false, defaultValue = "not-needed") String qwenApiKey,
			@ApiParam(value = "提示词") @RequestParam(value = "prompt", required = false, defaultValue = "对图片进行分类，类型有\"身份证正面\"，\"身份证反面\"，\"出生医学证明\"，\"常驻人口登记卡\"，\"居民户口簿信息\"，如果不是以上类型，请返回\"未分类\"，仅返回上述分类结果") String prompt,
			@ApiParam(value = "最大tokens") @RequestParam(value = "max_tokens", required = false, defaultValue = "16384") Integer maxTokens,
			@ApiParam(value = "最大段落数") @RequestParam(value = "max_paras", required = false, defaultValue = "2000") Integer maxParas,
			@ApiParam(value = "最大项目数") @RequestParam(value = "max_items", required = false, defaultValue = "5") Integer maxItems) {

		List<MultipartFile> images = extractFiles(request, "images");
		if (images == null || images.isEmpty()) {
			throw new IllegalArgumentException("未提供图片");
		}
		logger.info("收到classify请求: images={}", images.size());
		return classificationService.classifyImage(images, qwenServer, qwenModel, qwenApiKey, prompt, maxTokens);
	}

	private List<MultipartFile> extractFiles(HttpServletRequest request, String fieldName) {
		if (!(request instanceof MultipartHttpServletRequest)) {
			logger.error("请求不是multipart类型");
			return new ArrayList<>();
		}

		MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
		List<MultipartFile> files = new ArrayList<>();
		Set<String> addedFiles = new HashSet<>();

		// 获取指定字段的所有文件
		List<MultipartFile> fieldFiles = multipartRequest.getFiles(fieldName);
		if (fieldFiles != null && !fieldFiles.isEmpty()) {
			for (MultipartFile f : fieldFiles) {
				if (f != null && !f.isEmpty()) {
					String key = f.getOriginalFilename() + "_" + f.getSize();
					if (!addedFiles.contains(key)) {
						files.add(f);
						addedFiles.add(key);
					}
				}
			}
		}

		logger.info("提取到{}个文件", files.size());
		return files;
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public String handleIllegalArgument(IllegalArgumentException e) {
		return e.getMessage();
	}

	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public String handleException(Exception e) {
		logger.error("请求处理失败", e);
		return "请求处理失败: " + e.getMessage();
	}
}

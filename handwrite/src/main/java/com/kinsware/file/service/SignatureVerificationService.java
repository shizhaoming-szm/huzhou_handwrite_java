package com.kinsware.file.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinsware.file.config.AppProperties;
import com.kinsware.file.dto.MixResponse;
import com.kinsware.file.util.ImageUtils;
import com.kinsware.file.util.LlmClient;
import com.kinsware.file.util.TempFileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SignatureVerificationService {

	private static final Logger logger = LoggerFactory.getLogger(SignatureVerificationService.class);
	private static final ObjectMapper mapper = new ObjectMapper();

	private final TempFileManager tempFileManager = new TempFileManager();

	public MixResponse verifySignature(List<MultipartFile> images, String qwenServer, String qwenModel,
			String qwenApiKey, String cgServer, String cgModel, String cgApiKey,
			String prompt, int maxTokens) {
		if (images == null || images.isEmpty()) {
			throw new IllegalArgumentException("未提供图片");
		}

		List<Path> tempPaths = new ArrayList<>();
		try {
			logger.info("接收文件: {} 个", images.size());
			for (MultipartFile image : images) {
				byte[] data = image.getBytes();
				String ext = getExtension(image.getOriginalFilename());
				Path tempPath = tempFileManager.createTempFile(data, ext);
				tempPaths.add(tempPath);
			}
			logger.info("生成临时文件: {} 个", tempPaths.size());

			return runPipeline(tempPaths, qwenServer, qwenModel, qwenApiKey,
					cgServer, cgModel, cgApiKey, prompt, maxTokens);

		} catch (Exception e) {
			logger.error("签名验证失败", e);
			throw new RuntimeException("签名验证失败: " + e.getMessage(), e);
		} finally {
			cleanupTempFiles(tempPaths);
		}
	}

	private MixResponse runPipeline(List<Path> imagesPaths, String qwenServer, String qwenModel,
			String qwenApiKey, String cgServer, String cgModel, String cgApiKey,
			String prompt, int maxTokens) throws IOException {
		List<String> dataUrls = new ArrayList<>();
		for (Path p : imagesPaths) {
			byte[] data = ImageUtils.readFile(p);
			String mime = ImageUtils.getMimeType(p.toString());
			dataUrls.add(ImageUtils.bytesToDataUrl(data, mime));
		}
		logger.info("读取图片完成: {} 张", imagesPaths.size());

		LlmClient qwenClient = new LlmClient(qwenServer, qwenApiKey);
		try {
			List<String> qwenModels = qwenClient.listModels();
			if (!qwenModels.isEmpty() && !qwenModels.contains(qwenModel)) {
				return createErrorResponse("Qwen模型不可用: " + qwenModel, qwenModels);
			}

			long startTime = System.currentTimeMillis();
			String qwenText = visionRecognition(qwenClient, qwenModel, dataUrls, prompt);
			double duration = (System.currentTimeMillis() - startTime) / 1000.0;

			logger.info("视觉识别结束: duration={}s, text_len={}",
					String.format("%.3f", duration), qwenText.length());

			String preview = qwenText.length() > 400 ? qwenText.substring(0, 400) + "..." : qwenText;
			logger.info("识别文本预览: {}", preview);

			String instruct = "仅返回JSON，包含键：姓名(string)、签名一致(boolean)、理由(string)。" +
					"如果无法判断签名一致，则将签名一致设为false。" +
					"识别文本如下：\n" + qwenText;

			logger.info("文本抽取开始: model={}, max_tokens={}, instruct_len={}",
					cgModel, maxTokens, instruct.length());

			LlmClient cgClient = new LlmClient(cgServer, cgApiKey);
			try {
				String content = cgClient.chatCompletion(cgModel,
						createMessages(instruct), maxTokens, true);

				logger.info("文本抽取结束: output_len={}", content.length());
				logger.info("文本抽取原始输出: {}", content);

				Map<String, Object> data = parseJsonResponse(content);
				return buildResponse(qwenText, duration, content, data);

			} finally {
				cgClient.close();
			}

		} finally {
			qwenClient.close();
		}
	}

	private String visionRecognition(LlmClient client, String model,
			List<String> dataUrls, String prompt) throws IOException {
		List<Map<String, Object>> messages = new ArrayList<>();

		List<Map<String, Object>> content = new ArrayList<>();
		content.add(createTextContent(prompt));
		for (String url : dataUrls) {
			content.add(createImageContent(url));
		}

		Map<String, Object> userMessage = new LinkedHashMap<>();
		userMessage.put("role", "user");
		userMessage.put("content", content);
		messages.add(userMessage);

		logger.info("视觉识别开始: images={}, prompt_len={}",
				dataUrls.size(), prompt.length());

		return client.chatCompletion(model, messages, 0, true);
	}

	private Map<String, Object> parseJsonResponse(String content) {
		try {
			return mapper.readValue(content, Map.class);
		} catch (Exception e) {
			String cleaned = cleanCodeBlocks(content);
			try {
				return mapper.readValue(cleaned, Map.class);
			} catch (Exception ex) {
				return extractJsonObject(cleaned);
			}
		}
	}

	private String cleanCodeBlocks(String content) {
		return content.replaceAll("```[\\s\\S]*?```", "")
				.replaceAll("```json\\s*([\\s\\S]*?)```", "$1")
				.replaceAll("```\\s*([\\s\\S]*?)```", "$1")
				.trim();
	}

	private Map<String, Object> extractJsonObject(String content) {
		Pattern pattern = Pattern.compile("\\{[^\\}]+\\}");
		Matcher matcher = pattern.matcher(content);
		while (matcher.find()) {
			try {
				return mapper.readValue(matcher.group(), Map.class);
			} catch (Exception ignored) {
			}
		}
		return new HashMap<>();
	}

	private MixResponse buildResponse(String qwenText, double duration,
			String raw, Map<String, Object> data) {
		MixResponse response = new MixResponse();
		response.setQwenText(qwenText);
		response.setDuration(duration);
		response.setRaw(data);

		MixResponse.Concepts concepts = new MixResponse.Concepts();

		String name = getStringValue(data, "姓名", "name");
		Boolean match = getBooleanValue(data, "签名一致", "signature_match");

		concepts.set姓名(name != null && !name.isEmpty() ? Arrays.asList(name) : new ArrayList<>());
		concepts.set签名一致(Arrays.asList(match != null ? match : false));

		response.setConcepts(concepts);
		return response;
	}

	private String getStringValue(Map<String, Object> data, String... keys) {
		for (String key : keys) {
			Object value = data.get(key);
			if (value instanceof String && !((String) value).isEmpty()) {
				return (String) value;
			}
		}
		return "";
	}

	private Boolean getBooleanValue(Map<String, Object> data, String... keys) {
		for (String key : keys) {
			Object value = data.get(key);
			if (value instanceof Boolean) {
				return (Boolean) value;
			}
		}
		return false;
	}

	private MixResponse createErrorResponse(String error, List<String> availableModels) {
		MixResponse response = new MixResponse();
		Map<String, Object> raw = new HashMap<>();
		raw.put("error", error);
		raw.put("available_models", availableModels);
		response.setRaw(raw);
		return response;
	}

	private List<Map<String, Object>> createMessages(String instruct) {
		List<Map<String, Object>> messages = new ArrayList<>();
		Map<String, Object> message = new LinkedHashMap<>();
		message.put("role", "user");
		message.put("content", instruct);
		messages.add(message);
		return messages;
	}

	private Map<String, Object> createTextContent(String text) {
		Map<String, Object> content = new LinkedHashMap<>();
		content.put("type", "text");
		content.put("text", text);
		return content;
	}

	private Map<String, Object> createImageContent(String url) {
		Map<String, Object> content = new LinkedHashMap<>();
		content.put("type", "image_url");
		Map<String, Object> imageObj = new LinkedHashMap<>();
		imageObj.put("url", url);
		content.put("image_url", imageObj);
		return content;
	}

	private String getExtension(String filename) {
		if (filename == null) {
			return ".png";
		}
		int lastDot = filename.lastIndexOf('.');
		return lastDot > 0 ? filename.substring(lastDot) : ".png";
	}

	private void cleanupTempFiles(List<Path> paths) {
		for (Path path : paths) {
			try {
				tempFileManager.register(path);
				ImageUtils.deleteFile(path);
			} catch (Exception e) {
				logger.debug("Failed to delete temp file: {}", path);
			}
		}
	}
}

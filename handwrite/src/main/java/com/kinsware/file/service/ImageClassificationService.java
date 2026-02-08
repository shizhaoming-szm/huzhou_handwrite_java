package com.kinsware.file.service;

import com.kinsware.file.dto.ClassifyResponse;
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

@Service
public class ImageClassificationService {

	private static final Logger logger = LoggerFactory.getLogger(ImageClassificationService.class);

	private final TempFileManager tempFileManager = new TempFileManager();

	public ClassifyResponse classifyImage(List<MultipartFile> images, String qwenServer, String qwenModel,
			String qwenApiKey, String prompt, int maxTokens) {
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

			return runPipelineClassify(tempPaths, qwenServer, qwenModel, qwenApiKey, prompt, maxTokens);

		} catch (Exception e) {
			logger.error("图片分类失败", e);
			throw new RuntimeException("图片分类失败: " + e.getMessage(), e);
		} finally {
			cleanupTempFiles(tempPaths);
		}
	}

	private ClassifyResponse runPipelineClassify(List<Path> imagesPaths, String qwenServer, String qwenModel,
			String qwenApiKey, String prompt, int maxTokens) throws IOException {
		List<String> dataUrls = new ArrayList<>();
		for (Path p : imagesPaths) {
			byte[] data = ImageUtils.readFile(p);
			String mime = ImageUtils.getMimeType(p.toString());
			dataUrls.add(ImageUtils.bytesToDataUrl(data, mime));
		}
		logger.info("读取图片完成: {} 张", imagesPaths.size());

		LlmClient client = new LlmClient(qwenServer, qwenApiKey);
		try {
			List<String> models = client.listModels();
			if (!models.isEmpty() && !models.contains(qwenModel)) {
				return createErrorResponse("Qwen模型不可用: " + qwenModel);
			}

			String qwenText = visionRecognition(client, qwenModel, dataUrls, prompt);
			logger.info("视觉识别结束: text_len={}", qwenText.length());
			logger.info("识别文本预览: {}", qwenText);

			return buildResponse(qwenText);

		} finally {
			client.close();
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

	private ClassifyResponse buildResponse(String classification) {
		ClassifyResponse response = new ClassifyResponse();

		ClassifyResponse.ResponseBody body = new ClassifyResponse.ResponseBody();
		body.setLlmResult(Arrays.asList(classification));
		body.setOcrResult("");

		response.setResponseBody(body);
		response.setMessage("成功");
		response.setErrorCode(0);

		return response;
	}

	private ClassifyResponse createErrorResponse(String error) {
		ClassifyResponse response = new ClassifyResponse();
		response.setMessage(error);
		response.setErrorCode(502);
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
				ImageUtils.deleteFile(path);
			} catch (Exception e) {
				logger.debug("Failed to delete temp file: {}", path);
			}
		}
	}
}

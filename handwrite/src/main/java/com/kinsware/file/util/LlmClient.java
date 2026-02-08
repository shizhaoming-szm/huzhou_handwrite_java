package com.kinsware.file.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class LlmClient {

	private static final Logger logger = LoggerFactory.getLogger(LlmClient.class);
	private static final ObjectMapper mapper = new ObjectMapper();

	private final OkHttpClient httpClient;
	private final String server;
	private final String apiKey;

	public LlmClient(String server, String apiKey) {
		this.apiKey = apiKey;
		// 处理服务器URL
		String normalizedServer = server;
		// 如果URL以 /v1/chat/completions 结尾，去掉它
		if (normalizedServer.endsWith("/v1/chat/completions")) {
			normalizedServer = normalizedServer.substring(0, normalizedServer.length() - "/chat/completions".length());
		}
		// 确保URL以 /v1 结尾
		if (!normalizedServer.endsWith("/v1")) {
			normalizedServer = normalizedServer + "/v1";
		}
		this.server = normalizedServer;
		this.httpClient = new OkHttpClient.Builder()
				.connectTimeout(60, TimeUnit.SECONDS)
				.readTimeout(300, TimeUnit.SECONDS)
				.writeTimeout(60, TimeUnit.SECONDS)
				.connectionPool(new ConnectionPool(50, 5, TimeUnit.MINUTES))
				.build();
	}

	public List<String> listModels() {
		Request request = new Request.Builder()
				.url(server + "/models")
				.addHeader("Authorization", "Bearer " + apiKey)
				.get()
				.build();

		try (Response response = httpClient.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				return Collections.emptyList();
			}
			String body = response.body().string();
			JsonNode root = mapper.readTree(body);
			JsonNode data = root.get("data");
			List<String> models = new ArrayList<>();
			if (data != null && data.isArray()) {
				for (JsonNode item : data) {
					JsonNode id = item.get("id");
					if (id != null) {
						models.add(id.asText());
					}
				}
			}
			return models;
		} catch (IOException e) {
			logger.error("Failed to list models", e);
		}
		return Collections.emptyList();
	}

	public String chatCompletion(String model, List<Map<String, Object>> messages,
			int maxTokens, boolean stream) throws IOException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model);
		body.put("messages", messages);
		body.put("temperature", 0);
		body.put("stream", stream);
		if (maxTokens > 0) {
			body.put("max_tokens", maxTokens);
		}

		Request request = new Request.Builder()
				.url(server + "/chat/completions")
				.addHeader("Authorization", "Bearer " + apiKey)
				.addHeader("Content-Type", "application/json")
				.post(RequestBody.create(mapper.writeValueAsString(body),
						MediaType.parse("application/json")))
				.build();

		if (stream) {
			return chatCompletionStream(request);
		} else {
			try (Response response = httpClient.newCall(request).execute()) {
				if (!response.isSuccessful()) {
					throw new IOException("Request failed: " + response.code());
				}
				String responseBody = response.body().string();
				JsonNode root = mapper.readTree(responseBody);
				JsonNode choices = root.get("choices");
				if (choices != null && choices.isArray() && choices.size() > 0) {
					JsonNode message = choices.get(0).get("message");
					if (message != null) {
						JsonNode content = message.get("content");
						return content != null ? content.asText() : "";
					}
				}
				return "";
			}
		}
	}

	private String chatCompletionStream(Request request) throws IOException {
		StringBuilder result = new StringBuilder();
		try (Response response = httpClient.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				throw new IOException("Request failed: " + response.code());
			}
			if (response.body() == null) {
				return "";
			}

			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(response.body().byteStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.startsWith("data: ")) {
						String data = line.substring(6);
						if ("[DONE]".equals(data)) {
							break;
						}
						try {
							JsonNode chunk = mapper.readTree(data);
							JsonNode choices = chunk.get("choices");
							if (choices != null && choices.isArray() && choices.size() > 0) {
								JsonNode delta = choices.get(0).get("delta");
								if (delta != null) {
									JsonNode content = delta.get("content");
									if (content != null && !content.isNull()) {
										result.append(content.asText());
									}
								}
							}
						} catch (Exception e) {
							logger.debug("Failed to parse chunk: {}", data);
						}
					}
				}
			}
		}
		return result.toString();
	}

	public void close() {
		httpClient.dispatcher().executorService().shutdown();
		httpClient.connectionPool().evictAll();
	}
}

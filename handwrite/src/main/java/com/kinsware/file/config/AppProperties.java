package com.kinsware.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

	private String tempDir;
	private QwenProperties qwen = new QwenProperties();
	private ContextGemProperties contextgem = new ContextGemProperties();
	private String classifyServer;
	private String classifyModel;

	@Data
	public static class QwenProperties {
		private String server;
		private String model;
		private String apiKey;
	}

	@Data
	public static class ContextGemProperties {
		private String server;
		private String model;
		private String apiKey;
	}
}

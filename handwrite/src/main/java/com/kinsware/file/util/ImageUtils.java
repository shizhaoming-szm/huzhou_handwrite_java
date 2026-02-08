package com.kinsware.file.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

public class ImageUtils {

	public static String getMimeType(String path) {
		String ext = getFileExtension(path).toLowerCase();
		switch (ext) {
			case "jpg":
			case "jpeg":
				return "image/jpeg";
			case "png":
				return "image/png";
			case "webp":
				return "image/webp";
			case "bmp":
				return "image/bmp";
			default:
				return "application/octet-stream";
		}
	}

	public static String getFileExtension(String path) {
		int lastDot = path.lastIndexOf('.');
		return lastDot > 0 ? path.substring(lastDot + 1) : "";
	}

	public static String bytesToDataUrl(byte[] data, String mime) {
		String base64 = Base64.getEncoder().encodeToString(data);
		return "data:" + mime + ";base64," + base64;
	}

	public static String generateTempFilePath(String tempDir, String extension) {
		String filename = "_tmp_" + UUID.randomUUID().toString().replace("-", "") +
				"_" + System.currentTimeMillis() + extension;
		return tempDir + "/" + filename;
	}

	public static byte[] readFile(Path path) throws IOException {
		return Files.readAllBytes(path);
	}

	public static void writeFile(Path path, byte[] data) throws IOException {
		Files.createDirectories(path.getParent());
		Files.write(path, data);
	}

	public static void deleteFile(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			// 静默删除
		}
	}
}

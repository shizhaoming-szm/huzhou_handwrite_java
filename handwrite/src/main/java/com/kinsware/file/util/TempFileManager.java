package com.kinsware.file.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class TempFileManager {

	private static final Logger logger = LoggerFactory.getLogger(TempFileManager.class);
	private final Set<Path> tempFiles = Collections.synchronizedSet(new HashSet<>());

	public Path createTempFile(byte[] data, String extension) throws IOException {
		Path tempDir = Files.createTempDirectory("handwrite");
		String filename = "_tmp_" + UUID.randomUUID().toString().replace("-", "") +
				"_" + System.currentTimeMillis() + extension;
		Path filePath = tempDir.resolve(filename);
		Files.write(filePath, data);
		tempFiles.add(filePath);
		return filePath;
	}

	public void register(Path path) {
		tempFiles.add(path);
	}

	public void cleanupAll() {
		for (Path path : tempFiles) {
			try {
				Files.deleteIfExists(path);
				Path parent = path.getParent();
				if (parent != null && parent.toString().contains("handwrite")) {
					Files.deleteIfExists(parent);
				}
			} catch (IOException e) {
				logger.debug("Failed to delete temp file: {}", path);
			}
		}
		tempFiles.clear();
	}
}

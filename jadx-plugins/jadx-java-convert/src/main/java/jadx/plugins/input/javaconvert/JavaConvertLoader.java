package jadx.plugins.input.javaconvert;

import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.api.plugins.utils.CommonFileUtils;
import jadx.api.plugins.utils.ZipSecurity;

public class JavaConvertLoader {
	private static final Logger LOG = LoggerFactory.getLogger(JavaConvertLoader.class);

	private final JavaConvertOptions options;

	public JavaConvertLoader(JavaConvertOptions options) {
		this.options = options;
	}

	public ConvertResult process(List<File> input) {
		ConvertResult result = new ConvertResult();
		processJars(input, result);
		processAars(input, result);
		processClassFiles(input, result);
		return result;
	}

	private void processJars(List<File> input, ConvertResult result) {
		input.stream()
				.filter(file -> file.getName().endsWith(".jar"))
				.forEach(path -> {
					try {
						convertJar(result, path);
					} catch (Exception e) {
						LOG.error("Failed to convert file: {}", path.getAbsolutePath(), e);
					}
				});
	}

	private void processClassFiles(List<File> input, ConvertResult result) {
		List<File> clsFiles = input.stream()
				.filter(file -> file.getName().endsWith(".class"))
				.collect(Collectors.toList());
		if (clsFiles.isEmpty()) {
			return;
		}
		try {
			LOG.debug("Converting class files ...");
			File jarFile = File.createTempFile("jadx-", ".jar");
			try (JarOutputStream jo = new JarOutputStream(new FileOutputStream(jarFile))) {
				for (File file : clsFiles) {
					String clsName = AsmUtils.getNameFromClassFile(file);
					if (clsName == null || !ZipSecurity.isValidZipEntryName(clsName)) {
						throw new IOException("Can't read class name from file: " + file);
					}
					addFileToJar(jo, file, clsName + ".class");
				}
			}
			result.addTempPath(jarFile);
			LOG.debug("Packed {} class files into jar: {}", clsFiles.size(), jarFile);
			convertJar(result, jarFile);
		} catch (Exception e) {
			LOG.error("Error process class files", e);
		}
	}

	private void processAars(List<File> input, ConvertResult result) {
		input.stream()
				.filter(file -> file.getName().endsWith(".aar"))
				.forEach(path -> ZipSecurity.readZipEntries(path, (entry, in) -> {
					try {
						String entryName = entry.getName();
						if (entryName.endsWith(".jar")) {
							File tempJar = CommonFileUtils.saveToTempFile(in, ".jar");
							result.addTempPath(tempJar);
							LOG.debug("Loading jar: {} ...", entryName);
							convertJar(result, tempJar);
						}
					} catch (Exception e) {
						LOG.error("Failed to process zip entry: {}", entry, e);
					}
				}));
	}

	private void convertJar(ConvertResult result, File path) throws Exception {
		if (repackAndConvertJar(result, path)) {
			return;
		}
		convertSimpleJar(result, path);
	}

	private boolean repackAndConvertJar(ConvertResult result, File path) throws Exception {
		// check if jar need a full repackage
		Boolean repackNeeded = ZipSecurity.visitZipEntries(path, (zipFile, zipEntry) -> {
			String entryName = zipEntry.getName();
			if (zipEntry.isDirectory()) {
				if (entryName.equals("BOOT-INF/")) {
					return true; // Spring Boot jar
				}
				if (entryName.equals("META-INF/versions/")) {
					return true; // exclude duplicated classes
				}
			}
			if (entryName.endsWith(".jar")) {
				return true; // contains sub jars
			}
			if (entryName.endsWith("module-info.class")) {
				return true; // need to exclude module files
			}
			return null;
		});
		if (!Objects.equals(repackNeeded, Boolean.TRUE)) {
			return false;
		}
		LOG.debug("Repacking jar file: {} ...", path.getAbsolutePath());
		File jarFile = File.createTempFile("jadx-classes-", ".jar");
		result.addTempPath(jarFile);
		try (JarOutputStream jo = new JarOutputStream(new FileOutputStream(jarFile))) {
			ZipSecurity.readZipEntries(path, (entry, in) -> {
				try {
					String entryName = entry.getName();
					if (entryName.endsWith(".class")) {
						if (entryName.endsWith("module-info.class")
								|| entryName.startsWith("META-INF/versions/")) {
							LOG.debug(" exclude: {}", entryName);
							return;
						}
						byte[] clsFileContent = CommonFileUtils.loadBytes(in);
						String clsName = AsmUtils.getNameFromClassFile(clsFileContent);
						if (clsName == null || !ZipSecurity.isValidZipEntryName(clsName)) {
							throw new IOException("Can't read class name from file: " + entryName);
						}
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
							addJarEntry(jo, clsName + ".class", clsFileContent, entry.getLastModifiedTime().toMillis());
						} else {
							addJarEntry(jo, clsName + ".class", clsFileContent, 0);
						}
					} else if (entryName.endsWith(".jar")) {
						File tempJar = CommonFileUtils.saveToTempFile(in, ".jar");
						result.addTempPath(tempJar);
						convertJar(result, tempJar);
					}
				} catch (Exception e) {
					LOG.error("Failed to process jar entry: {} in {}", entry, path, e);
				}
			});
		}
		convertSimpleJar(result, jarFile);
		return true;
	}

	private void convertSimpleJar(ConvertResult result, File path) throws Exception {
		File tempDirectory = Files.createTempDirectory("jadx-").toFile();
		result.addTempPath(tempDirectory);
		LOG.debug("Converting to dex ...");
		convert(path, tempDirectory);
		List<File> dexFiles = collectFilesInDir(tempDirectory);
		LOG.debug("Converted {} to {} dex", path.getAbsolutePath(), dexFiles.size());
		result.addConvertedFiles(dexFiles);
	}

	private void convert(File path, File tempDirectory) {
		JavaConvertOptions.Mode mode = options.getMode();
		switch (mode) {
			case DX:
				try {
					DxConverter.run(path, tempDirectory);
				} catch (Throwable e) {
					LOG.error("DX convert failed, path: {}", path, e);
				}
				break;

			case D8:
				try {
					D8Converter.run(path, tempDirectory, options);
				} catch (Throwable e) {
					LOG.error("D8 convert failed, path: {}", path, e);
				}
				break;

			case BOTH:
				try {
					DxConverter.run(path, tempDirectory);
				} catch (Throwable e) {
					LOG.warn("DX convert failed, trying D8, path: {}", path);
					try {
						D8Converter.run(path, tempDirectory, options);
					} catch (Throwable ex) {
						LOG.error("D8 convert failed: {}", ex.getMessage());
					}
				}
				break;
		}
	}

	private static List<File> collectFilesInDir(File tempDirectory) throws IOException {
		try (Stream<File> walk = Stream.of(tempDirectory.listFiles())) {
			return walk.filter(file -> !file.isDirectory())
					.filter(file -> file.getName().matches(".dex"))
					.collect(Collectors.toList());
		}
	}

	private static void addFileToJar(JarOutputStream jar, File source, String entryName) throws IOException {
		byte[] fileContent = Files.readAllBytes(source.toPath());
		long lastModifiedTime = source.lastModified();
		addJarEntry(jar, entryName, fileContent, lastModifiedTime);
	}

	private static void addJarEntry(JarOutputStream jar, String entryName, byte[] content,
			long modTime) throws IOException {
		JarEntry entry = new JarEntry(entryName);
		if (modTime != 0) {
			entry.setTime(modTime);
		}
		jar.putNextEntry(entry);
		jar.write(content);
		jar.closeEntry();
	}
}

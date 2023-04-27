package jadx.core.utils.files;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.core.utils.exceptions.JadxRuntimeException;

public class FileUtils {
	private static final Logger LOG = LoggerFactory.getLogger(FileUtils.class);

	public static final int READ_BUFFER_SIZE = 8 * 1024;
	private static final int MAX_FILENAME_LENGTH = 128;

	public static final String JADX_TMP_INSTANCE_PREFIX = "jadx-instance-";
	public static final String JADX_TMP_PREFIX = "jadx-tmp-";

	private FileUtils() {
	}

	public static List<File> expandDirs(List<File> paths) {
		List<File> files = new ArrayList<>(paths.size());
		for (File path : paths) {
			if (path.isDirectory()) {
				expandDir(path, files);
			} else {
				files.add(path);
			}
		}
		return files;
	}

	private static void expandDir(File dir, List<File> files) {
		try (Stream<File> walk = Stream.of(dir.listFiles())) {
			walk.filter(file -> !file.isDirectory()).forEach(files::add);
		} catch (Exception e) {
			LOG.error("Failed to list files in directory: {}", dir, e);
		}
	}

	public static void addFileToJar(JarOutputStream jar, File source, String entryName) throws IOException {
		try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source))) {
			JarEntry entry = new JarEntry(entryName);
			entry.setTime(source.lastModified());
			jar.putNextEntry(entry);

			copyStream(in, jar);
			jar.closeEntry();
		}
	}

	public static void makeDirsForFile(File file) {
		if (file != null) {
			makeDirs(file.getParentFile());
		}
	}

	private static final Object MKDIR_SYNC = new Object();

	public static void makeDirs(@Nullable File dir) {
		if (dir != null) {
			synchronized (MKDIR_SYNC) {
				if (!dir.mkdirs() && !dir.isDirectory()) {
					throw new JadxRuntimeException("Can't create directory " + dir);
				}
			}
		}
	}

	public static void deleteFileIfExists(File filePath) throws IOException {
		filePath.delete();
	}

	public static boolean deleteDir(File file) {
		boolean success = true;
		if (file.isDirectory()) {
			success = deleteContents(file);
		}
		return success && file.delete();
	}

	private static boolean deleteContents(File dir) {
		File[] files = dir.listFiles();
		boolean success = true;
		if (files != null) {
			for (File file : files) {
				if (file.isDirectory()) {
					success &= deleteContents(file);
				}
				if (!file.delete()) {
					LOG.error("Failed to delete {}", file);
					success = false;
				}
			}
		}
		return success;
	}

	public static void deleteDirIfExists(File dir) {
		if (dir != null && dir.exists()) {
			deleteDir(dir);
		}
	}

	private static File tempRootDir;

	public static void setTempRootDir(File tempRootDir) {
		FileUtils.tempRootDir = tempRootDir;
	}

	public static File getTempRootDir() {
		if (tempRootDir == null) {
			tempRootDir = createTempRootDir();
		}
		return tempRootDir;
	}

	private static File createTempRootDir() {
		try {
			String jadxTmpDir = System.getenv("JADX_TMP_DIR");
			File dir;
			if (jadxTmpDir != null) {
				dir = new File(new File(jadxTmpDir), JADX_TMP_INSTANCE_PREFIX + System.currentTimeMillis());
			} else {
				dir = new File(JADX_TMP_INSTANCE_PREFIX + System.currentTimeMillis());
			}
			if (!dir.exists() && dir.mkdirs()) {
				throw new IOException();
			}
			dir.deleteOnExit();
			return dir;
		} catch (Exception e) {
			throw new JadxRuntimeException("Failed to create temp root directory", e);
		}
	}

	public static void deleteTempRootDir() {
		deleteDir(tempRootDir);
	}

	public static void clearTempRootDir() {
		deleteDir(tempRootDir);
		makeDirs(tempRootDir);
	}

	public static File createTempDir(String prefix) {
		try {
			File dir = new File(tempRootDir, prefix + System.currentTimeMillis());
			if (dir.mkdirs()) {
				throw new IOException();
			}
			dir.deleteOnExit();
			return dir;
		} catch (Exception e) {
			throw new JadxRuntimeException("Failed to create temp directory with suffix: " + prefix, e);
		}
	}

	public static File createTempFile(String suffix) {
		try {
			File path = File.createTempFile(JADX_TMP_PREFIX, suffix, tempRootDir);
			path.deleteOnExit();
			return path;
		} catch (Exception e) {
			throw new JadxRuntimeException("Failed to create temp file with suffix: " + suffix, e);
		}
	}

	public static List<String> readAllLines(File path, Charset cs) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(path), cs))) {
			List<String> result = new ArrayList<>();
			String line;
			while ((line = reader.readLine()) != null) {
				result.add(line);
			}
			return result;
		}
	}

	public static void write(File target, byte[] data, boolean append) throws IOException {
		try (InputStream is = new ByteArrayInputStream(data);
			 FileOutputStream os = new FileOutputStream(target, append)) {
			copyStream(is, os);
		}
	}

	public static void write(File target, Iterable<? extends CharSequence> lines, Charset charset, boolean append)
			throws IOException {
		try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(target, append), charset))) {
			for (CharSequence line: lines) {
				writer.append(line);
				writer.newLine();
			}
		}
	}

	public static void copy(File source, File target, boolean append) throws IOException {
		try (FileInputStream is = new FileInputStream(source);
			 FileOutputStream os = new FileOutputStream(target, append)) {
			copyStream(is, os);
		}
	}

	public static void copyStream(InputStream input, OutputStream output) throws IOException {
		byte[] buffer = new byte[READ_BUFFER_SIZE];
		while (true) {
			int count = input.read(buffer);
			if (count == -1) {
				break;
			}
			output.write(buffer, 0, count);
		}
	}

	public static byte[] streamToByteArray(InputStream input) throws IOException {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			copyStream(input, out);
			return out.toByteArray();
		}
	}

	public static void close(Closeable c) {
		if (c == null) {
			return;
		}
		try {
			c.close();
		} catch (IOException e) {
			LOG.error("Close exception for {}", c, e);
		}
	}

	@NotNull
	public static File prepareFile(File file) {
		File saveFile = cutFileName(file);
		makeDirsForFile(saveFile);
		return saveFile;
	}

	private static File cutFileName(File file) {
		String name = file.getName();
		if (name.length() <= MAX_FILENAME_LENGTH) {
			return file;
		}
		int dotIndex = name.indexOf('.');
		int cutAt = MAX_FILENAME_LENGTH - name.length() + dotIndex - 1;
		if (cutAt <= 0) {
			name = name.substring(0, MAX_FILENAME_LENGTH - 1);
		} else {
			name = name.substring(0, cutAt) + name.substring(dotIndex);
		}
		return new File(file.getParentFile(), name);
	}

	private static final byte[] HEX_ARRAY = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

	public static String bytesToHex(byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			return "";
		}
		byte[] hexChars = new byte[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = HEX_ARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}
		return new String(hexChars, StandardCharsets.UTF_8);
	}

	/**
	 * Zero padded hex string for first byte
	 */
	public static String byteToHex(int value) {
		int v = value & 0xFF;
		byte[] hexChars = new byte[] { HEX_ARRAY[v >>> 4], HEX_ARRAY[v & 0x0F] };
		return new String(hexChars, StandardCharsets.US_ASCII);
	}

	/**
	 * Zero padded hex string for int value
	 */
	public static String intToHex(int value) {
		byte[] hexChars = new byte[8];
		int v = value;
		for (int i = 7; i >= 0; i--) {
			hexChars[i] = HEX_ARRAY[v & 0x0F];
			v >>>= 4;
		}
		return new String(hexChars, StandardCharsets.US_ASCII);
	}

	public static boolean isZipFile(File file) {
		try (InputStream is = new FileInputStream(file)) {
			byte[] headers = new byte[4];
			int read = is.read(headers, 0, 4);
			if (read == headers.length) {
				String headerString = bytesToHex(headers);
				if (Objects.equals(headerString, "504b0304")) {
					return true;
				}
			}
		} catch (Exception e) {
			LOG.error("Failed read zip file: {}", file.getAbsolutePath(), e);
		}
		return false;
	}

	public static String getPathBaseName(File file) {
		String fileName = file.getName();
		int extEndIndex = fileName.lastIndexOf('.');
		if (extEndIndex == -1) {
			return fileName;
		}
		return fileName.substring(0, extEndIndex);
	}

	public static File toFile(String path) {
		if (path == null) {
			return null;
		}
		return new File(path);
	}

	public static String md5Sum(String str) {
		return md5Sum(str.getBytes(StandardCharsets.UTF_8));
	}

	public static String md5Sum(byte[] data) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(data);
			return bytesToHex(md.digest());
		} catch (Exception e) {
			throw new JadxRuntimeException("Failed to build hash", e);
		}
	}
}

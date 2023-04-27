package jadx.plugins.input.javaconvert;

import java.io.Closeable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConvertResult implements Closeable {
	private static final Logger LOG = LoggerFactory.getLogger(ConvertResult.class);

	private final List<File> converted = new ArrayList<>();
	private final List<File> tmpPaths = new ArrayList<>();

	public List<File> getConverted() {
		return converted;
	}

	public void addConvertedFiles(List<File> paths) {
		converted.addAll(paths);
	}

	public void addTempPath(File path) {
		tmpPaths.add(path);
	}

	public boolean isEmpty() {
		return converted.isEmpty();
	}

	@Override
	public void close() {
		for (File tmpPath : tmpPaths) {
			delete(tmpPath);
		}
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private static void delete(File path) {
		if (path.isDirectory()) {
			deleteDir(path);
			return;
		}
		path.delete();
	}

	private static boolean deleteDir(File dir) {
		File[] content = dir.listFiles();
		if (content != null) {
			for (File file : content) {
				deleteDir(file);
			}
		}
		return dir.delete();
	}

	@Override
	public String toString() {
		return "ConvertResult{converted=" + converted + ", tmpPaths=" + tmpPaths + '}';
	}
}

package jadx.plugins.input.smali;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.jf.smali.Smali;
import org.jf.smali.SmaliOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmaliConvert implements Closeable {
	private static final Logger LOG = LoggerFactory.getLogger(SmaliConvert.class);

	@Nullable
	private File tmpDex;

	public boolean execute(List<File> input) {
		List<File> smaliFiles = filterSmaliFiles(input);
		if (smaliFiles.isEmpty()) {
			return false;
		}
		LOG.debug("Compiling smali files: {}", smaliFiles.size());
		try {
			this.tmpDex = File.createTempFile("jadx-", ".dex");
			if (compileSmali(tmpDex, smaliFiles)) {
				return true;
			}
		} catch (Exception e) {
			LOG.error("Smali process error", e);
		}
		close();
		return false;
	}

	private static boolean compileSmali(File output, List<File> inputFiles) throws IOException {
		SmaliOptions options = new SmaliOptions();
		options.outputDexFile = output.getAbsolutePath();
		options.verboseErrors = true;
		options.apiLevel = 27; // TODO: add as plugin option

		List<String> inputFileNames = inputFiles.stream()
				.map(File::getAbsolutePath)
				.distinct()
				.collect(Collectors.toList());

		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			boolean result = collectSystemErrors(out, () -> Smali.assemble(options, inputFileNames));
			if (!result) {
				LOG.error("Smali compilation error:\n{}", out);
			}
			return result;
		}
	}

	private static boolean collectSystemErrors(OutputStream out, Callable<Boolean> exec) {
		PrintStream systemErr = System.err;
		try (PrintStream err = new PrintStream(out)) {
			System.setErr(err);
			try {
				return exec.call();
			} catch (Exception e) {
				e.printStackTrace(err);
				return false;
			}
		} finally {
			System.setErr(systemErr);
		}
	}

	private List<File> filterSmaliFiles(List<File> input) {
		return input.stream()
				.filter(file -> file.getName().endsWith(".smali"))
				.collect(Collectors.toList());
	}

	public List<File> getDexFiles() {
		if (tmpDex == null) {
			return Collections.emptyList();
		}
		return Collections.singletonList(tmpDex);
	}

	@Override
	public void close() {
		if (tmpDex != null && tmpDex.exists() && !tmpDex.delete()) {
			LOG.error("Failed to remove tmp dex file: {}", tmpDex);
		}
	}
}

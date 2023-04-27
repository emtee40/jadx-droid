package jadx.plugins.input.raung;

import java.io.Closeable;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.skylot.raung.asm.RaungAsm;

public class RaungConvert implements Closeable {
	private static final Logger LOG = LoggerFactory.getLogger(RaungConvert.class);

	@Nullable
	private File tmpJar;

	public boolean execute(List<File> input) {
		List<Path> inputPaths = input.stream().map(File::toPath).collect(Collectors.toList());
		List<File> raungInputs = filterRaungFiles(input);
		if (raungInputs.isEmpty()) {
			return false;
		}
		try {
			this.tmpJar = File.createTempFile("jadx-raung-", ".jar");
			RaungAsm.create()
					.output(tmpJar.toPath())
					.inputs(inputPaths)
					.execute();
			return true;
		} catch (Exception e) {
			LOG.error("Raung process error", e);
		}
		close();
		return false;
	}

	private List<File> filterRaungFiles(List<File> input) {
		return input.stream()
				.filter(file -> file.getName().endsWith(".raung"))
				.collect(Collectors.toList());
	}

	public List<File> getFiles() {
		if (tmpJar == null) {
			return Collections.emptyList();
		}
		return Collections.singletonList(tmpJar);
	}

	@Override
	public void close() {
		if (tmpJar != null && tmpJar.exists() && !tmpJar.delete()) {
			LOG.error("Failed to remove tmp jar file: {}", tmpJar);
		}
	}
}

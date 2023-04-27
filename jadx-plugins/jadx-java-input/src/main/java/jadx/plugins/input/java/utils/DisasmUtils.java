package jadx.plugins.input.java.utils;

import android.os.Build;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.skylot.raung.disasm.RaungDisasm;

public class DisasmUtils {
	private static final Logger LOG = LoggerFactory.getLogger(DisasmUtils.class);

	public static String get(byte[] bytes) {
		return useRaung(bytes);
	}

	private static String useRaung(byte[] bytes) {
		return RaungDisasm.create()
				.executeForInputStream(new ByteArrayInputStream(bytes));
	}

	/**
	 * Use javap as a temporary disassembler for java bytecode
	 * Don't remove! Useful for debug.
	 */
	private static String useSystemJavaP(byte[] bytes) {
		try {
			File tmpCls = null;
			try {
				tmpCls = File.createTempFile("jadx", ".class");
				try (FileOutputStream os = new FileOutputStream(tmpCls)) {
					os.write(bytes);
				}
				Process process = Runtime.getRuntime().exec(new String[] {
						"javap", "-constants", "-v", "-p", "-c",
						tmpCls.getAbsolutePath()
				});
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
					process.waitFor(2, TimeUnit.SECONDS);
				} else process.wait(2000);
				return inputStreamToString(process.getInputStream());
			} finally {
				if (tmpCls != null) {
					tmpCls.delete();
				}
			}
		} catch (Exception e) {
			LOG.error("Java class disasm error", e);
			return "error";
		}
	}

	public static String inputStreamToString(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8 * 1024];
		while (true) {
			int r = in.read(buf);
			if (r == -1) {
				break;
			}
			out.write(buf, 0, r);
		}
		return out.toString();
	}
}

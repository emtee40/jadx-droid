package jadx.plugins.input.javaconvert;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.objectweb.asm.ClassReader;

public class AsmUtils {

	public static String getNameFromClassFile(File file) throws IOException {
		try (InputStream in = new FileInputStream(file)) {
			return getClassFullName(new ClassReader(in));
		}
	}

	public static String getNameFromClassFile(byte[] content) throws IOException {
		return getClassFullName(new ClassReader(content));
	}

	private static String getClassFullName(ClassReader classReader) {
		return classReader.getClassName();
	}

}

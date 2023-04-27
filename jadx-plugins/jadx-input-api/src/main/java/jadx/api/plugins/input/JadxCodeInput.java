package jadx.api.plugins.input;

import java.io.File;
import java.util.List;

public interface JadxCodeInput {
	ICodeLoader loadFiles(List<File> input);
}

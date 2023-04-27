package jadx.plugins.input.raung;

import java.io.File;
import java.util.List;

import jadx.api.plugins.JadxPlugin;
import jadx.api.plugins.JadxPluginContext;
import jadx.api.plugins.JadxPluginInfo;
import jadx.api.plugins.input.ICodeLoader;
import jadx.api.plugins.input.JadxCodeInput;
import jadx.api.plugins.input.data.impl.EmptyCodeLoader;
import jadx.plugins.input.java.JavaInputPlugin;

public class RaungInputPlugin implements JadxPlugin, JadxCodeInput {

	@Override
	public JadxPluginInfo getPluginInfo() {
		return new JadxPluginInfo(
				"raung-input",
				"RaungInput",
				"Load .raung files");
	}

	@Override
	public void init(JadxPluginContext context) {
		context.addCodeInput(this);
	}

	@Override
	public ICodeLoader loadFiles(List<File> input) {
		RaungConvert convert = new RaungConvert();
		if (!convert.execute(input)) {
			return EmptyCodeLoader.INSTANCE;
		}
		return JavaInputPlugin.loadClassFiles(convert.getFiles(), convert);
	}
}

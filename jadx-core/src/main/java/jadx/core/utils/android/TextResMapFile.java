package jadx.core.utils.android;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jadx.core.utils.exceptions.JadxRuntimeException;
import jadx.core.utils.files.FileUtils;

public class TextResMapFile {
	private static final int SPLIT_POS = 8;

	public static Map<Integer, String> read(InputStream is) {
		try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			Map<Integer, String> resMap = new HashMap<>();
			while (true) {
				String line = br.readLine();
				if (line == null) {
					break;
				}
				parseLine(resMap, line);
			}
			return resMap;
		} catch (Exception e) {
			throw new JadxRuntimeException("Failed to read res-map file", e);
		}
	}

	private static void parseLine(Map<Integer, String> resMap, String line) {
		int id = Integer.parseInt(line.substring(0, SPLIT_POS), 16);
		String name = line.substring(SPLIT_POS + 1);
		resMap.put(id, name);
	}

	public static Map<Integer, String> read(File resMapFile) {
		try (InputStream in = new FileInputStream(resMapFile)) {
			return read(in);
		} catch (Exception e) {
			throw new JadxRuntimeException("Failed to read res-map file", e);
		}
	}

	public static void write(File resMapFile, Map<Integer, String> inputResMap) {
		try {
			Map<Integer, String> resMap = new TreeMap<>(inputResMap);
			List<String> lines = new ArrayList<>(resMap.size());
			for (Map.Entry<Integer, String> entry : resMap.entrySet()) {
				lines.add(String.format("%08x=%s", entry.getKey(), entry.getValue()));
			}
			FileUtils.write(resMapFile, lines, StandardCharsets.UTF_8, false);
		} catch (Exception e) {
			throw new JadxRuntimeException("Failed to write res-map file", e);
		}
	}
}

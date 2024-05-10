package freder;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public final class Utils {
    private Utils() {
        // prevent instantiation
    }

	public static String readFileToString(String filePath) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
		FileReader fr = new FileReader(filePath);
        try (var reader = new BufferedReader(fr)) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
        }
        return stringBuilder.toString();
    }

    public static HashMap<String, String> readFeedUrls(String filePath) throws IOException {
		String contents = readFileToString(filePath);
		var gson = new Gson();
		var type = new TypeToken<HashMap<String, String>>(){};
		return gson.fromJson(contents, type);
    }
}

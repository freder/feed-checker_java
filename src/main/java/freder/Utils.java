package freder;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;

public final class Utils {
	static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

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

	public static Date getLastCheckTime(String filePath) throws Exception {
		String contents = readFileToString(filePath);
		String dateStr = contents.lines().limit(1).findFirst().orElse(null);
		if (dateStr == null) {
			throw new Exception("File is empty");
		}
		return dateFormat.parse(dateStr);
	}

	public static void writeLastCheckTime(String filePath, final Date date) throws IOException {
		String dateStr = dateFormat.format(date);
		try (FileWriter writer = new FileWriter(filePath)) {
			writer.write(dateStr);
		}
	}

	public static String fetchFeedBody(HttpClient client, String url) throws IOException, InterruptedException, URISyntaxException {
		var uri = new URI(url);
		var req = HttpRequest.newBuilder(uri).GET().build();
		var res = client.send(req, HttpResponse.BodyHandlers.ofString());
		return res.body();
	}

	public static Date consolidateDates(SyndEntry item) {
		Date datePublished = item.getPublishedDate();
		Date dateUpdated = item.getUpdatedDate();
		Date date = (datePublished != null)
			? datePublished
			: dateUpdated;
			item.setUpdatedDate(date);
		return date;
	}

	public static List<SyndEntry> getNewItems(final SyndFeed feed, final Date lastCheckDate) {
		List<SyndEntry> allItems = feed.getEntries();
		List<SyndEntry> newItems = allItems.stream()
			.map((item) -> {
				consolidateDates(item);
				return item;
			})
			.filter((item) -> {
				Date date = item.getUpdatedDate();
				return date.after(lastCheckDate);
			})
			.collect(Collectors.toList());
		return newItems;
	}
}

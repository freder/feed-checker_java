package freder;

import java.io.IOException;
import java.io.StringReader;
import java.net.http.HttpClient;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.io.SyndFeedInput;

public class App {
	static final String feedsFilePath = "./feeds.json";
	static final int maxConcurrency = 4;

	private static void printUsageAndExit() {
		System.err.println("Usage:");
		System.err.println("<feed-checker> list");
		System.err.println("<feed-checker> check");
		System.exit(1);
	}

	private static void listFeeds() {
		HashMap<String, String> feedMap;
		try {
			feedMap = Utils.readFeedUrls(feedsFilePath);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
			return;
		}
		for (String key: feedMap.keySet()) {
			System.out.println(key + ": " + feedMap.get(key));
		}
	}

	private static void checkFeeds() {
		HashMap<String, String> feedMap;
		try {
			feedMap = Utils.readFeedUrls(feedsFilePath);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
			return;
		}

		var exec = Executors.newFixedThreadPool(maxConcurrency);
		var tasks = new ArrayList<Callable<Void>>();
		var results = new ConcurrentHashMap<String, List<SyndEntry>>();
		HttpClient client = HttpClient.newHttpClient();

		// TODO: use real date
		Date lastCheckDate = new GregorianCalendar(
			2024, Calendar.JANUARY, 1, 0, 0, 0
		).getTime();

		for (var entry: feedMap.entrySet()) {
			String name = entry.getKey();
			String url = entry.getValue();
			Callable<Void> task = () -> {
				System.out.print("."); // progress indicator
				String body;
				try {
					body = Utils.fetchFeedBody(client, url);
				} catch (Exception e) {
					e.printStackTrace();
					return null;
				}
				var feed = (new SyndFeedInput()).build(new StringReader(body));
				List<SyndEntry> newItems = Utils.getNewItems(feed, lastCheckDate);
				results.put(name, newItems);
				return null;
			};
			tasks.add(task);
		}

		try {
			exec.invokeAll(tasks);
		} catch (InterruptedException e) {
			e.printStackTrace();
			System.exit(1);
			return;
		} finally {
			exec.close();
		}

		System.out.println();

		int totalNew = 0;
		for (var items: results.values()) {
			totalNew += items.size();
		}
		if (totalNew == 0) {
			System.out.println("No new items");
			return;
		}

		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		for (String name: results.keySet()) {
			var items = results.get(name);
			if (items.size() > 0) {
				System.out.println();
				System.out.println(name + ":");
				items.forEach((item) -> {
					String date = dateFormat.format(item.getUpdatedDate());
					System.out.println(
						String.format("- [%s] %s", date, item.getTitle())
					);
					System.out.println(
						String.format("  %s", item.getLink())
					);
				});
			}
		}
	}

	public static void main(String[] args) {
		if (args.length != 1) {
			printUsageAndExit();
		}

		switch (args[0]) {
			case "list":
				listFeeds();
				break;

			case "check":
				checkFeeds();
				break;

			default:
				break;
		}
	}
}

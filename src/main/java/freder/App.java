package freder;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.io.SyndFeedInput;

public class App {
	static final String feedsFilePath = "./feeds.json";

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

		var exec = Executors.newFixedThreadPool(4);
		var tasks = new ArrayList<Callable<String>>();
		HttpClient client = HttpClient.newHttpClient();
		Date lastCheckDate = new GregorianCalendar(
			2024, Calendar.JANUARY, 1, 0, 0, 0
		).getTime();
		var results = new ConcurrentHashMap<String, List<SyndEntry>>();

		for (var entry: feedMap.entrySet()) {
			String name = entry.getKey();
			String url = entry.getValue();
			tasks.add(() -> {
				System.out.print(".");
				var uri = new URI(url);
				HttpRequest req = HttpRequest.newBuilder(uri).GET().build();
				var res = client.send(req, HttpResponse.BodyHandlers.ofString());
				String body = res.body();
				var feed = (new SyndFeedInput()).build(new StringReader(body));

				List<SyndEntry> allItems = feed.getEntries();
				List<SyndEntry> newItems = allItems.stream()
					.filter((item) -> {
						Date datePublished = item.getPublishedDate();
						Date dateUpdated = item.getUpdatedDate();
						Date date = (datePublished != null)
							? datePublished
							: dateUpdated;
						if (date.after(lastCheckDate)) {
							item.setUpdatedDate(date);
							return true;
						}
						return false;
					})
					.collect(Collectors.toList());

				results.put(name, newItems);

				return ""; // TODO: remove
			});
		}

		// List<Future<String>> futures;
		try {
			/* futures = */ exec.invokeAll(tasks);
		} catch (InterruptedException e) {
			e.printStackTrace();
			System.exit(1);
			return;
		} finally {
			exec.close();
		}

		System.out.println();

		// for (var f: futures) {
		// 	try {
		// 		System.out.println(f.get());
		// 	} catch (InterruptedException | ExecutionException e) {
		// 		e.printStackTrace();
		// 		continue;
		// 	}
		// }

		int totalNew = 0;
		for (var items: results.values()) {
			totalNew += items.size();
		}

		if (totalNew == 0) {
			System.out.println("No new items");
			return;
		}

		SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy");
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

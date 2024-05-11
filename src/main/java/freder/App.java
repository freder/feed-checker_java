package freder;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
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
	static final String dbFileName = "db.sqlite";
	static final String feedsFilePath = "./feeds.json";
	static final String lastCheckTimeFilePath = "./last-check.txt";
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

		Date _lastCheckTime;
		try {
			_lastCheckTime = Utils.getLastCheckTime(lastCheckTimeFilePath);
		} catch (Exception e) {
			System.err.println("Failed to read last check date file");
			_lastCheckTime = new GregorianCalendar(
				1970, Calendar.JANUARY, 1, 0, 0, 0
			).getTime();
		}
		final Date lastCheckTime = _lastCheckTime;

		try {
			Date now = new Date();
			Utils.writeLastCheckTime(lastCheckTimeFilePath, now);
		} catch (IOException e) {
			System.err.println("Failed to write last check time file");
			e.printStackTrace();
		}

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
				var feed = Utils.parseFeed(body);

				var newItems = Utils.getNewItems(feed, lastCheckTime);
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
			exec.shutdown();
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

		SimpleDateFormat outputDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		for (String name: results.keySet()) {
			var items = results.get(name);
			if (items.size() > 0) {
				System.out.println();
				System.out.println(name + ":");
				items.forEach((item) -> {
					String date = outputDateFormat.format(item.getUpdatedDate());
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

	public static void addFeed(String feedUrl) {
		try {
			var url = new URI(feedUrl);
			var conn = DriverManager.getConnection("jdbc:sqlite:" + dbFileName);
			DatabaseUtils.addFeed(conn, url);
			conn.close();
		} catch (URISyntaxException e) {
			System.err.println("Not a valid URL");
			System.exit(1);
		} catch (SQLException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public static void removeFeed(String feedUrl) {
		// TODO: implement
	}

	public static void main(String[] args) {
		// ensure db / table exists
		try {
			DatabaseUtils.createTable(dbFileName);
		} catch (SQLException e) {
			System.err.println("Failed to create database");
		}

		if (args.length == 1) {
			switch (args[0]) {
				case "list":
					listFeeds();
					break;
				case "check":
					checkFeeds();
					break;
				default:
					printUsageAndExit();
					break;
			}
		} else if (args.length == 2) {
			switch (args[0]) {
				case "add":
					addFeed(args[1]);
					break;
				case "remove":
					removeFeed(args[1]);
					break;
				default:
					printUsageAndExit();
					break;
			}
		} else {
			printUsageAndExit();
		}
	}
}

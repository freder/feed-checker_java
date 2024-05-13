package freder.feedchecker;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import com.rometools.rome.feed.synd.SyndEntry;

public class App {
	static final String dbFileName = "db.sqlite";
	static final int maxConcurrency = 4;

	private static void printUsageAndExit() {
		System.err.println("Usage:");
		System.err.println("<feed-checker> add <feed-url>");
		System.err.println("<feed-checker> list <feed-url>");
		System.err.println("<feed-checker> list");
		System.err.println("<feed-checker> check");
		System.exit(1);
	}

	private static void listFeeds() {
		ArrayList<FeedsTableRow> feeds;
		try (var conn = DatabaseUtils.getConnection(dbFileName)) {
			feeds = DatabaseUtils.getFeeds(conn);
		} catch (SQLException e) {
			System.err.println("Failed to get feeds");
			System.exit(1);
			return;
		}

		for (var entry: feeds) {
			System.out.println(
				entry.title() + ": " + entry.url()
			);
		}
	}

	private static void checkFeeds() {
		ArrayList<FeedsTableRow> feeds;
		Connection conn;

		try {
			conn = DatabaseUtils.getConnection(dbFileName);
		} catch (SQLException e) {
			System.err.println("Failed to connect to database");
			System.exit(1);
			return;
		}

		try {
			feeds = DatabaseUtils.getFeeds(conn);
		} catch (SQLException e) {
			System.err.println("Failed to get feeds");
			System.exit(1);
			return;
		}

		var exec = Executors.newFixedThreadPool(maxConcurrency);
		var tasks = new ArrayList<Callable<Void>>();
		var results = new ConcurrentHashMap<String, List<SyndEntry>>();
		HttpClient client = HttpClient.newHttpClient();

		for (var entry: feeds) {
			Callable<Void> task = () -> {
				System.out.print("."); // progress indicator
				String body;
				try {
					body = Utils.fetchFeedBody(client, entry.url());
				} catch (Exception e) {
					e.printStackTrace();
					return null;
				}

				var feed = Utils.parseFeed(body);

				// update last check time
				DatabaseUtils.updateFeedLastCheck(
					conn, entry.url(), new Date()
				);

				var newItems = Utils.getNewItems(feed, entry.lastCheck());
				newItems.sort((SyndEntry a, SyndEntry b) -> {
					return b.getUpdatedDate().compareTo(a.getUpdatedDate());
				});

				results.put(entry.title(), newItems);
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

		try {
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
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
		URI url;
		try {
			url = new URI(feedUrl);
		} catch (URISyntaxException e) {
			System.err.println("Not a valid URL");
			System.exit(1);
			return;
		}

		try (var conn = DatabaseUtils.getConnection(dbFileName)) {
			DatabaseUtils.addFeed(conn, url);
		} catch (SQLException e) {
			if (e.toString().contains("SQLITE_CONSTRAINT_UNIQUE")) {
				System.err.println("Feed exists already");
			} else {
				e.printStackTrace();
			}
			System.exit(1);
			return;
		}
	}

	public static void removeFeed(String feedUrl) {
		try (var conn = DatabaseUtils.getConnection(dbFileName)) {
			DatabaseUtils.removeFeed(conn, feedUrl);
		} catch (SQLException e) {
			e.printStackTrace();
			System.exit(1);
			return;
		}
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

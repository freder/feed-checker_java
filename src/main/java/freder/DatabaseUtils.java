package freder;

import java.net.URI;
import java.net.http.HttpClient;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DatabaseUtils {
	static final String tableName = "feeds";

	private DatabaseUtils() {
		// prevent instantiation
	}

	public static void createTable(String fileName) throws SQLException {
		try (var conn = DriverManager.getConnection("jdbc:sqlite:" + fileName)) {
			var statement = conn.createStatement();
			String queryStr = """
				CREATE TABLE IF NOT EXISTS %s (
					id         INTEGER PRIMARY KEY,
					url        TEXT NOT NULL UNIQUE,
					title      TEXT NOT NULL,
					last_check TEXT NOT NULL
				)
			""";
			queryStr = String.format(queryStr, tableName);
			statement.executeUpdate(queryStr);
		} catch (SQLException e) {
			throw e;
		}
	}

	public static void addFeed(Connection conn, URI url) throws SQLException {
		String urlStr = url.toString();

		String title;
		try {
			HttpClient client = HttpClient.newHttpClient();
			var body = Utils.fetchFeedBody(client, urlStr);
			var feed = Utils.parseFeed(body);
			title = feed.getTitle();
		} catch (Exception e) {
			System.err.println("Failed to fetch feed");
			System.exit(1);
			return;
		}

		var statement = conn.createStatement();

		String cols = "url, title, last_check";

		String[] values = { urlStr, title, "" };
		String valuesStr = Stream.of(values)
			.map((val) -> String.format("'%s'", val))
			.collect(Collectors.joining(", "));

		statement.executeUpdate(
			String.format(
				"insert into %s (%s) values (%s)",
				tableName, cols, valuesStr
			)
		);
	}
}

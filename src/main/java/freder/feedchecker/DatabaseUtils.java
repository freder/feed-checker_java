package freder.feedchecker;

import java.net.URI;
import java.net.http.HttpClient;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DatabaseUtils {
	static final String connPrefix = "jdbc:sqlite:";
	static final String tableName = "feeds";
	static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	static final Date fallbackDate = new GregorianCalendar(
		1970, Calendar.JANUARY, 1, 0, 0, 0
	).getTime();

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

		String cols = "url, title, last_check";

		String[] values = { urlStr, title, "" };
		String valuesStr = Stream.of(values)
			.map((val) -> String.format("'%s'", val))
			.collect(Collectors.joining(", "));

		var statement = conn.createStatement();
		statement.executeUpdate(
			String.format(
				"INSERT INTO %s (%s) VALUES (%s)",
				tableName, cols, valuesStr
			)
		);
	}

	public static void removeFeed(
		Connection conn,
		String url
	) throws SQLException {
		var statement = conn.createStatement();
		statement.executeUpdate(
			String.format(
				"DELETE FROM %s WHERE url = '%s'",
				tableName, url
			)
		);
	}

	public static void updateFeedLastCheck(
		Connection conn,
		String url,
		Date date
	) throws SQLException {
		var statement = conn.createStatement();
		statement.executeUpdate(
			String.format(
				"UPDATE %s SET last_check = '%s' WHERE url = '%s'",
				tableName,
				dateFormat.format(date),
				url
			)
		);
	}

	public static ArrayList<FeedsTableRow> getFeeds(
		Connection conn
	) throws SQLException {
		var statement = conn.createStatement();
		var rs = statement.executeQuery(
			String.format("SELECT * FROM %s", tableName)
		);

		var rows = new ArrayList<FeedsTableRow>();
		while(rs.next()) {
			Date lastCheckTime;
			try {
				lastCheckTime = dateFormat.parse(rs.getString("last_check"));
			} catch (ParseException e) {
				lastCheckTime = fallbackDate;
			}

			rows.add(
				new FeedsTableRow(
					rs.getInt("id"),
					rs.getString("url"),
					rs.getString("title"),
					lastCheckTime
				)
			);
		}
		return rows;
	}
}

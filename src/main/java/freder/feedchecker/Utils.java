package freder.feedchecker;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;

public final class Utils {
	private Utils() {
		// prevent instantiation
	}

	public static String fetchFeedBody(
		HttpClient client,
		String url
	) throws IOException, InterruptedException, URISyntaxException {
		var uri = new URI(url);
		var req = HttpRequest.newBuilder(uri).GET().build();
		var res = client.send(req, HttpResponse.BodyHandlers.ofString());
		return res.body();
	}

	public static SyndFeed parseFeed(String body) throws FeedException {
		return (new SyndFeedInput()).build(new StringReader(body));
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

	public static List<SyndEntry> getNewItems(
		final SyndFeed feed,
		final Date lastCheckDate
	) {
		var allItems = feed.getEntries();
		var newItems = allItems.stream()
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

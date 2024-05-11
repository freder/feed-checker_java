package freder.feedchecker;

import java.util.Date;

public record FeedsTableRow(
	int id,
	String url,
	String title,
	Date lastCheck
) {};

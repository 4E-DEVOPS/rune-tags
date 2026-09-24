package com.runetags.reports;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReportSummary {
	private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern(
			"MMMM dd, yyyy", Locale.ENGLISH);

	String source;
	int caseCount;

	String rsn;
	LocalDateTime publishedDate;

	/*
	 * Current RuneWatch records publish hash as the public case identifier.
	 * ReportCaseService also accepts short_code and normalizes either value into caseId.
	 */
	String caseId;

	String reason;
	String evidenceRating;

	public String getSourceLabel() {
		return "WDR".equalsIgnoreCase(source)
				? "WE DO RAIDS"
				: "RUNEWATCH";
	}

	public String getFormattedDate() {
		return publishedDate != null
				? publishedDate.format(DISPLAY_DATE_FORMAT)
				: "";
	}

	public boolean hasCaseLink() {
		if (caseId == null) {
			return false;
		}

		final String value = caseId.trim();
		return !value.isEmpty() && value.matches("[A-Za-z0-9_-]+");
	}

	public String getCaseUrl() {
		return hasCaseLink()
				? "https://runewatch.com/case/" + caseId.trim()
				: null;
	}
}

package com.runetags.reports;

import java.time.LocalDateTime;

import org.junit.Assert;
import org.junit.Test;

public class ReportSummaryTest
{
    /*
     * TESTS
     */

    @Test
    public void wdrSourceUsesWeDoRaidsLabel()
    {
        Assert.assertEquals(
                "WE DO RAIDS",
                ReportSummary.builder()
                        .source(
                                "WDR")
                        .build()
                        .getSourceLabel());

        Assert.assertEquals(
                "WE DO RAIDS",
                ReportSummary.builder()
                        .source(
                                "wdr")
                        .build()
                        .getSourceLabel());
    }

    @Test
    public void nonWdrSourceUsesRuneWatchLabel()
    {
        Assert.assertEquals(
                "RUNEWATCH",
                ReportSummary.builder()
                        .source(
                                "RW")
                        .build()
                        .getSourceLabel());

        Assert.assertEquals(
                "RUNEWATCH",
                ReportSummary.builder()
                        .source(
                                null)
                        .build()
                        .getSourceLabel());

        Assert.assertEquals(
                "RUNEWATCH",
                ReportSummary.builder()
                        .source(
                                "unknown")
                        .build()
                        .getSourceLabel());
    }

    @Test
    public void formattedDateUsesExpectedEnglishDisplayFormat()
    {
        final ReportSummary summary =
                ReportSummary.builder()
                        .publishedDate(
                                LocalDateTime.of(
                                        2026,
                                        9,
                                        11,
                                        14,
                                        35,
                                        42))
                        .build();

        Assert.assertEquals(
                "September 11, 2026",
                summary.getFormattedDate());
    }

    @Test
    public void formattedDateIsEmptyWhenDateIsMissing()
    {
        Assert.assertEquals(
                "",
                ReportSummary.builder()
                        .publishedDate(
                                null)
                        .build()
                        .getFormattedDate());
    }

    @Test
    public void validCaseIdentifiersHaveCaseLinks()
    {
        Assert.assertTrue(
                summaryWithCaseId(
                        "abc123")
                        .hasCaseLink());

        Assert.assertTrue(
                summaryWithCaseId(
                        "ABC_123")
                        .hasCaseLink());

        Assert.assertTrue(
                summaryWithCaseId(
                        "case-123")
                        .hasCaseLink());

        Assert.assertTrue(
                summaryWithCaseId(
                        "  case_123-ABC  ")
                        .hasCaseLink());
    }

    @Test
    public void nullBlankAndInvalidCaseIdentifiersHaveNoCaseLink()
    {
        Assert.assertFalse(
                summaryWithCaseId(
                        null)
                        .hasCaseLink());

        Assert.assertFalse(
                summaryWithCaseId(
                        "")
                        .hasCaseLink());

        Assert.assertFalse(
                summaryWithCaseId(
                        "   ")
                        .hasCaseLink());

        Assert.assertFalse(
                summaryWithCaseId(
                        "case 123")
                        .hasCaseLink());

        Assert.assertFalse(
                summaryWithCaseId(
                        "case/123")
                        .hasCaseLink());

        Assert.assertFalse(
                summaryWithCaseId(
                        "case?123")
                        .hasCaseLink());

        Assert.assertFalse(
                summaryWithCaseId(
                        "case#123")
                        .hasCaseLink());
    }

    @Test
    public void caseUrlUsesTrimmedValidatedIdentifier()
    {
        Assert.assertEquals(
                "https://runewatch.com/case/case_123-ABC",
                summaryWithCaseId(
                        "  case_123-ABC  ")
                        .getCaseUrl());
    }

    @Test
    public void invalidCaseIdentifierHasNoCaseUrl()
    {
        Assert.assertNull(
                summaryWithCaseId(
                        null)
                        .getCaseUrl());

        Assert.assertNull(
                summaryWithCaseId(
                        "   ")
                        .getCaseUrl());

        Assert.assertNull(
                summaryWithCaseId(
                        "case/123")
                        .getCaseUrl());
    }

    @Test
    public void builderPreservesReportData()
    {
        final LocalDateTime publishedDate =
                LocalDateTime.of(
                        2026,
                        8,
                        30,
                        12,
                        15);

        final ReportSummary summary =
                ReportSummary.builder()
                        .source(
                                "RW")
                        .caseCount(
                                3)
                        .rsn(
                                "Santa Clause")
                        .publishedDate(
                                publishedDate)
                        .caseId(
                                "abc_123")
                        .reason(
                                "Test reason")
                        .evidenceRating(
                                "High")
                        .build();

        Assert.assertEquals(
                "RW",
                summary.getSource());

        Assert.assertEquals(
                3,
                summary.getCaseCount());

        Assert.assertEquals(
                "Santa Clause",
                summary.getRsn());

        Assert.assertEquals(
                publishedDate,
                summary.getPublishedDate());

        Assert.assertEquals(
                "abc_123",
                summary.getCaseId());

        Assert.assertEquals(
                "Test reason",
                summary.getReason());

        Assert.assertEquals(
                "High",
                summary.getEvidenceRating());
    }

    /*
     * HELPERS
     */

    private static ReportSummary summaryWithCaseId(
            String caseId)
    {
        return ReportSummary.builder()
                .caseId(
                        caseId)
                .build();
    }
}
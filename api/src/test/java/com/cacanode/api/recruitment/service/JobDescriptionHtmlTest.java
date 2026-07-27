package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobDescriptionHtmlTest {
    private final JobDescriptionHtml descriptions = new JobDescriptionHtml();

    @Test
    void keepsOnlyRecruitmentFormattingAndDerivesDeterministicPlainText() {
        var value = descriptions.normalize("""
                <h2 class='hero'>About <strong>the role</strong></h2>
                <p style='color:red' onclick='steal()'>Build<br>safe products.</p>
                <ul><li>Java</li><li><em>Postgres</em></li></ul>
                <blockquote>Own outcomes</blockquote>
                <img src=x><video src=x></video><script>alert('x')</script>
                """);

        assertEquals("About the role\nBuild\nsafe products.\nJava\nPostgres\nOwn outcomes", value.plainText());
        assertTrue(value.html().contains("<h2>About <strong>the role</strong></h2>"));
        assertFalse(value.html().contains("class="));
        assertFalse(value.html().contains("style="));
        assertFalse(value.html().contains("onclick"));
        assertFalse(value.html().contains("script"));
        assertFalse(value.html().contains("img"));
        assertFalse(value.html().contains("video"));
    }

    @Test
    void rejectsUnsafeProtocolsAndAddsSafeLinkRelations() {
        var value = descriptions.normalize("""
                <p><a href="javascript:alert(1)">bad</a>
                <a href="data:text/html,x">data</a>
                <a href="https://example.com" target="_blank">safe</a>
                <a href="mailto:jobs@example.com">mail</a></p>
                """);

        assertFalse(value.html().contains("javascript:"));
        assertFalse(value.html().contains("data:text"));
        assertFalse(value.html().contains("target="));
        assertTrue(value.html().contains("href=\"https://example.com\" rel=\"nofollow noopener noreferrer\""));
        assertTrue(value.html().contains("href=\"mailto:jobs@example.com\" rel=\"nofollow noopener noreferrer\""));
    }

    @Test
    void toleratesMalformedHtmlButRejectsEmptyVisibleContent() {
        var value = descriptions.normalize("<p>First <strong>line<p>Second");
        assertEquals("First line\nSecond", value.plainText());
        assertThrows(BadRequestException.class, () -> descriptions.normalize("<p><br></p><script>alert(1)</script>"));
    }
}

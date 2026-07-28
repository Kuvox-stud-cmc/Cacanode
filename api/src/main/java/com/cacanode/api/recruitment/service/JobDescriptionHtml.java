package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class JobDescriptionHtml {
    private static final Set<String> BLOCKS = Set.of("p", "h2", "h3", "li", "blockquote");
    private static final Safelist ALLOWED = Safelist.none()
            .addTags("p", "h2", "h3", "strong", "b", "em", "i", "ol", "ul", "li", "a", "blockquote", "br")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer");

    public Normalized normalize(String html) {
        Document dirty = Jsoup.parseBodyFragment(html == null ? "" : html);
        Document clean = new Cleaner(ALLOWED).clean(dirty);
        clean.outputSettings().prettyPrint(false);
        String plain = plainText(clean.body());
        if (plain.isBlank()) throw new BadRequestException("Job description must contain visible text");
        return new Normalized(clean.body().html(), plain);
    }

    private static String plainText(Element body) {
        StringBuilder value = new StringBuilder();
        for (Node child : body.childNodes()) append(child, value);
        String[] lines = value.toString().replace('\u00a0', ' ').split("\\R", -1);
        StringBuilder normalized = new StringBuilder();
        for (String line : lines) {
            String text = line.replaceAll("[\\t\\x0B\\f\\r ]+", " ").strip();
            if (text.isEmpty()) continue;
            if (!normalized.isEmpty()) normalized.append('\n');
            normalized.append(text);
        }
        return normalized.toString();
    }

    private static void append(Node node, StringBuilder value) {
        if (node instanceof TextNode text) value.append(text.getWholeText());
        else if (node instanceof Element element) {
            if (element.normalName().equals("br")) value.append('\n');
            else {
                for (Node child : element.childNodes()) append(child, value);
                if (BLOCKS.contains(element.normalName())) value.append('\n');
            }
        }
    }

    public record Normalized(String html, String plainText) {}
}

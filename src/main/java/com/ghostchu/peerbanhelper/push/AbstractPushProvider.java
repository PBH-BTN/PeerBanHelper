package com.ghostchu.peerbanhelper.push;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.text.TextContentRenderer;

public abstract class AbstractPushProvider implements PushProvider {
    private static final Parser parser = Parser.builder().build();
    private static final TextContentRenderer plainRenderer = TextContentRenderer.builder().build();
    private static final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

    public String stripMarkdown(String markdown) {
        Node document = parser.parse(markdown);
        return plainRenderer.render(document);
    }

    public String markdown2Html(String markdown) {
        Node document = parser.parse(markdown);
        return htmlRenderer.render(document);
    }
}

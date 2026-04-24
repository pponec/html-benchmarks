package org.ujorm.benchmark;

import gg.jte.TemplateEngine;
import gg.jte.output.WriterOutput;
import htmlflow.HtmlFlow;
import org.jsoup.Jsoup;
import org.ujorm.tools.web.AbstractHtmlElement;
import org.ujorm.tools.xml.config.HtmlConfig;

import java.io.IOException;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static j2html.TagCreator.*;

/** Verifies benchmark output structure consistency across all tested frameworks. */
final class HtmlOutputEquivalenceVerifier {

    private HtmlOutputEquivalenceVerifier() {
    }

    static void verifyEquivalentHtmlOutputs(
            List<HtmlBenchmark.Fortune> fortunes,
            HtmlConfig config,
            TemplateEngine jteEngine
    ) {
        try {
            var expected = renderUjorm(fortunes, config);
            var expectedBodyElementCount = countBodyElements(expected);
            var results = new LinkedHashMap<String, String>();
            results.put("J2html", renderJ2html(fortunes));
            results.put("Jte", renderJte(fortunes, jteEngine));
            results.put("HtmlFlow", renderHtmlFlow(fortunes));
            results.put("StringBuilder", renderStringBuilder(fortunes));
            results.put("Dom4j", renderDom4j(fortunes));
            results.put("Jsoup", renderJsoup(fortunes));
            results.put("KotlinxHtml", renderKotlinxHtml(fortunes));

            for (var entry : results.entrySet()) {
                var framework = entry.getKey();
                var html = entry.getValue();
                var bodyElementCount = countBodyElements(html);
                if (expectedBodyElementCount != bodyElementCount) {
                    throw new IllegalStateException("Body element count mismatch against UjormElement for "
                            + framework + ": expected " + expectedBodyElementCount + ", actual "
                            + bodyElementCount + ".");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to verify benchmark HTML equivalence.", e);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to verify benchmark HTML equivalence.", e);
        }
    }

    private static String renderUjorm(List<HtmlBenchmark.Fortune> fortunes, HtmlConfig config) throws IOException {
        try (var writer = new StringWriter();
             var html = AbstractHtmlElement.of(writer, config)) {
            try (var body = html.addBody()) {
                try (var table = body.addTable()) {
                    for (var fortune : fortunes) {
                        try (var row = table.addTableRow()) {
                            row.addTableDetail().addText(fortune.id());
                            row.addTableDetail().addText(fortune.message());
                            row.addTableDetail().addText(fortune.author());
                        }
                    }
                }
            }
            return writer.toString();
        }
    }

    private static String renderJ2html(List<HtmlBenchmark.Fortune> fortunes) {
        return html(
                body(
                        table(
                                each(fortunes, fortune -> tr(
                                        td(String.valueOf(fortune.id())),
                                        td(fortune.message()),
                                        td(fortune.author())
                                ))
                        )
                )
        ).render();
    }

    private static String renderJte(List<HtmlBenchmark.Fortune> fortunes, TemplateEngine jteEngine) throws IOException {
        var writer = new StringWriter();
        jteEngine.render("fortune.jte", Map.of("fortunes", fortunes), new WriterOutput(writer));
        return writer.toString();
    }

    private static String renderHtmlFlow(List<HtmlBenchmark.Fortune> fortunes) {
        return HtmlFlow.view(view -> view
                .html().body().table()
                .of(table -> {
                    for (var fortune : fortunes) {
                        table.tr()
                                .td().text(String.valueOf(fortune.id())).__()
                                .td().text(fortune.message()).__()
                                .td().text(fortune.author()).__()
                                .__();
                    }
                }).__().__().__()
        ).render();
    }

    private static String renderStringBuilder(List<HtmlBenchmark.Fortune> fortunes) {
        var html = new SafeHtmlStringBuilder(2048);
        html.raw("<html><body><table>");
        for (var fortune : fortunes) {
            html.openTag("tr")
                    .element("td", fortune.id())
                    .element("td", fortune.message())
                    .element("td", fortune.author())
                    .closeTag("tr");
        }
        html.raw("</table></body></html>");
        return html.toString();
    }

    private static String renderDom4j(List<HtmlBenchmark.Fortune> fortunes) throws IOException {
        var document = org.dom4j.DocumentHelper.createDocument();
        var html = document.addElement("html");
        var body = html.addElement("body");
        var table = body.addElement("table");

        for (var fortune : fortunes) {
            var tr = table.addElement("tr");
            tr.addElement("td").addText(String.valueOf(fortune.id()));
            tr.addElement("td").addText(fortune.message());
            tr.addElement("td").addText(fortune.author());
        }

        try (var writer = new StringWriter()) {
            var outputFormat = org.dom4j.io.OutputFormat.createCompactFormat();
            outputFormat.setSuppressDeclaration(true);
            var xmlWriter = new org.dom4j.io.XMLWriter(writer, outputFormat);
            xmlWriter.write(document);
            xmlWriter.flush();
            return writer.toString();
        }
    }

    private static String renderJsoup(List<HtmlBenchmark.Fortune> fortunes) {
        var doc = new org.jsoup.nodes.Document("");
        doc.outputSettings().prettyPrint(false);
        var table = doc.appendElement("html").appendElement("body").appendElement("table");

        for (var fortune : fortunes) {
            var tr = table.appendElement("tr");
            tr.appendElement("td").text(String.valueOf(fortune.id()));
            tr.appendElement("td").text(fortune.message());
            tr.appendElement("td").text(fortune.author());
        }

        return doc.outerHtml();
    }

    private static String renderKotlinxHtml(List<HtmlBenchmark.Fortune> fortunes) {
        var builder = new StringBuilder(2048);
        KotlinHtml.INSTANCE.renderFortunes(fortunes, builder);
        return builder.toString();
    }

    private static int countBodyElements(String html) {
        var body = Jsoup.parse(html).body();
        // Exclude the body node itself and count only inner elements.
        return body.getAllElements().size() - 1;
    }
}

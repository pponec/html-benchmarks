package org.ujorm.benchmark;

import gg.jte.TemplateEngine;
import gg.jte.output.WriterOutput;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

final class JteRenderer {

    private JteRenderer() {}

    static void render(HtmlBenchmark.BenchmarkModel model, HtmlBenchmark.Scenario scenario, TemplateEngine jteEngine, Writer writer) throws IOException {
        if (scenario.isComplex()) {
            jteEngine.render(
                    "complex-page.jte",
                    Map.of("fortunes", model.fortunes(), "menuItems", model.menuItems(), "showPromo", model.showPromo()),
                    new WriterOutput(writer)
            );
        } else {
            jteEngine.render("fortune.jte", Map.of("fortunes", model.fortunes()), new WriterOutput(writer));
        }
    }

    static String renderToString(HtmlBenchmark.BenchmarkModel model, HtmlBenchmark.Scenario scenario, TemplateEngine jteEngine) throws IOException {
        try (var writer = new StringWriter()) {
            render(model, scenario, jteEngine, writer);
            return writer.toString();
        }
    }
}

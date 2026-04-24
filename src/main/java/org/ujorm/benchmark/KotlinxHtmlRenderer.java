package org.ujorm.benchmark;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

final class KotlinxHtmlRenderer {

    private KotlinxHtmlRenderer() {}

    static void render(HtmlBenchmark.BenchmarkModel model, HtmlBenchmark.Scenario scenario, Writer writer) throws IOException {
        if (scenario.isComplex()) {
            KotlinHtml.INSTANCE.renderComplexPage(model.fortunes(), model.menuItems(), model.showPromo(), writer);
        } else {
            KotlinHtml.INSTANCE.renderFortunes(model.fortunes(), writer);
        }
    }

    static String renderToString(HtmlBenchmark.BenchmarkModel model, HtmlBenchmark.Scenario scenario) throws IOException {
        try (var writer = new StringWriter()) {
            render(model, scenario, writer);
            return writer.toString();
        }
    }
}

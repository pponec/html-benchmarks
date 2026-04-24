package org.ujorm.benchmark;

import gg.jte.TemplateEngine;
import org.jsoup.Jsoup;
import org.ujorm.tools.xml.config.HtmlConfig;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;

/** Verifies benchmark output structure consistency across all tested frameworks. */
final class HtmlOutputEquivalenceVerifier {

    private HtmlOutputEquivalenceVerifier() {
    }

    static void verifyEquivalentHtmlOutputs(
            List<HtmlBenchmark.Fortune> fortunes,
            List<String> menuItems,
            boolean showPromo,
            HtmlConfig config,
            TemplateEngine jteEngine,
            boolean complexLayout
    ) {
        try {
            var scenario = complexLayout ? HtmlBenchmark.Scenario.COMPLEX_100 : HtmlBenchmark.Scenario.SIMPLE_100;
            var model = new HtmlBenchmark.BenchmarkModel(fortunes, menuItems, showPromo);
            var expected = UjormRenderer.renderToString(model, scenario, config);
            var expectedBodyElementCount = countBodyElements(expected);
            var results = new LinkedHashMap<String, String>();
            results.put("J2html", J2htmlRenderer.renderToString(model, scenario));
            results.put("Jte", JteRenderer.renderToString(model, scenario, jteEngine));
            results.put("HtmlFlow", HtmlFlowRenderer.render(model, scenario));
            results.put("StringBuilder", SafeStringBuilderRenderer.render(model, scenario));
            results.put("Dom4j", Dom4jRenderer.renderToString(model, scenario));
            results.put("Jsoup", JsoupRenderer.render(model, scenario));
            results.put("KotlinxHtml", KotlinxHtmlRenderer.renderToString(model, scenario));

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

    private static int countBodyElements(String html) {
        var body = Jsoup.parse(html).body();
        // Exclude the body node itself and count only inner elements.
        return body.getAllElements().size() - 1;
    }

}

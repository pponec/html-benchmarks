package org.ujorm.benchmark;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.resolve.DirectoryCodeResolver;
import htmlflow.HtmlFlow;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.ujorm.tools.web.AbstractHtmlElement;
import org.ujorm.tools.xml.config.HtmlConfig;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static j2html.TagCreator.*;

/** JMH benchmark for HTML generation performance */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class HtmlBenchmark {

    private List<Fortune> fortunes;
    private TemplateEngine jteEngine;

    /** Setup the testing data and engines */
    @Setup
    public void setup() {
        this.fortunes = List.of(
                new Fortune(1, "A computer scientist is someone who fixes things that aren't broken."),
                new Fortune(2, "Feature: A bug with seniority."),
                new Fortune(3, "To err is human, but to really foul things up you need a computer.")
        );

        var codeResolver = new DirectoryCodeResolver(Path.of("src/main/resources"));
        this.jteEngine = TemplateEngine.create(codeResolver, ContentType.Html);
    }

    /** Benchmark for Ujorm Element HTML builder */
    @Benchmark
    public String benchmarkUjormElement() {
        var writer = new StringBuilder(512);

        var config = HtmlConfig.ofDefault();
        try (var html = AbstractHtmlElement.of(writer, config)) {
            try (var body = html.addBody()) {
                try (var table = body.addTable()) {
                    for (var fortune : this.fortunes) {
                        try (var row = table.addTableRow()) {
                            try (var td1 = row.addTableDetail()) {
                                td1.addText(fortune.id());
                            }
                            try (var td2 = row.addTableDetail()) {
                                td2.addText(fortune.message());
                            }
                        }
                    }
                }
            }
        }
        return writer.toString();
    }

    /** Benchmark for standard StringBuilder fallback */
    @Benchmark
    public String benchmarkStringBuilder() {
        var builder = new StringBuilder();
        builder.append("<html><body><table>");
        for (var fortune : this.fortunes) {
            builder.append("<tr><td>").append(fortune.id()).append("</td>")
                    .append("<td>").append(fortune.message()).append("</td></tr>");
        }
        builder.append("</table></body></html>");

        return builder.toString();
    }

    /** Benchmark for j2html pure Java API */
    @Benchmark
    public String benchmarkJ2html() {
        return html(
                body(
                        table(
                                each(this.fortunes, fortune -> tr(
                                        td(String.valueOf(fortune.id())),
                                        td(fortune.message())
                                ))
                        )
                )
        ).render();
    }

    /** Benchmark for HtmlFlow API (V5) */
    @Benchmark
    public String benchmarkHtmlFlow() {
        return HtmlFlow.view(view -> view
                .html()
                .body()
                .table()
                .of(table -> {
                    for (var fortune : this.fortunes) {
                        table.tr()
                                .td().text(String.valueOf(fortune.id())).__()
                                .td().text(fortune.message()).__()
                                .__(); // tr
                    }
                })
                .__() // table
                .__() // body
                .__() // html
        ).render();
    }

    /** Benchmark for JTE template engine */
    @Benchmark
    public String benchmarkJte() {
        var output = new StringOutput();
        jteEngine.render("fortune.jte", Map.of("fortunes", this.fortunes), output);
        return output.toString();
    }

    /** Run the benchmark programmatically */
    public static void main(String[] args) throws RunnerException {
        var opt = new OptionsBuilder()
                .include(HtmlBenchmark.class.getSimpleName())
                .addProfiler("gc")
                .build();
        new Runner(opt).run();
    }
}
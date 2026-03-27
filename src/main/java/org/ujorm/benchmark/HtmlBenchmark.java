package org.ujorm.benchmark;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.WriterOutput;
import gg.jte.resolve.DirectoryCodeResolver;
import htmlflow.HtmlFlow;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.ujorm.tools.web.AbstractHtmlElement;
import org.ujorm.tools.xml.config.HtmlConfig;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

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
    private HtmlConfig config;
    private final LongAdder globalCharCount = new LongAdder();

    /** Setup the testing data and engines */
    @Setup
    public void setup() {
        this.config = HtmlConfig.ofDefault();
        this.fortunes = List.of(
                new Fortune(101, "John", "A computer scientist is someone who fixes things that aren't broken."),
                new Fortune(102, "Emily", "Feature: A bug with seniority."),
                new Fortune(103, "Michael", "To err is human, but to really foul things up you need a computer."),
                new Fortune(104, "Sarah", "Programmers are tools for converting caffeine into code."),
                new Fortune(105, "David", "There are 10 types of people: those who understand binary and those who don't."),
                new Fortune(106, "Jessica", "Debugging: Being the detective in a crime movie where you are also the murderer."),
                new Fortune(107, "James", "It's not a bug, it's an undocumented feature."),
                new Fortune(108, "Lisa", "Weeks of coding can save you hours of planning."),
                new Fortune(109, "Robert", "If at first you don’t succeed, call it version 1.0."),
                new Fortune(110, "Jane", "Computers make very fast, very accurate mistakes.")
        );
        var codeResolver = new DirectoryCodeResolver(Path.of("src/main/resources"));
        this.jteEngine = TemplateEngine.create(codeResolver, ContentType.Html);
        this.jteEngine.setTrimControlStructures(true);

        globalCharCount.reset();
    }

    /** Print the character count after each iteration */
    @TearDown(Level.Iteration)
    public void tearDown(BenchmarkParams params) {
        System.out.printf("%n[Verification] %s: %d characters generated.%n",
                params.getBenchmark(), globalCharCount.sumThenReset());
    }

    /** Benchmark for Ujorm Element HTML builder */
    @Benchmark
    public void benchmarkUjormElement(Blackhole bh) throws IOException {
        try (var writer = new CountingBlackholeWriter(bh, globalCharCount);
             var html = AbstractHtmlElement.of(writer, config)) {
            try (var body = html.addBody()) {
                try (var table = body.addTable()) {
                    for (var fortune : this.fortunes) {
                        try (var row = table.addTableRow()) {
                            row.addTableDetail().addText(fortune.id());
                            row.addTableDetail().addText(fortune.message());
                            row.addTableDetail().addText(fortune.author());
                        }
                    }
                }
            }
        }
    }

    /** Benchmark for j2html pure Java API */
    @Benchmark
    public void benchmarkJ2html(Blackhole bh) throws IOException {
        try (var writer = new CountingBlackholeWriter(bh, globalCharCount)) {
            html(
                    body(
                            table(
                                    each(this.fortunes, fortune -> tr(
                                            td(String.valueOf(fortune.id())),
                                            td(fortune.message()),
                                            td(fortune.author())
                                    ))
                            )
                    )
            ).render(writer);
        }
    }

    /** Benchmark for JTE template engine */
    @Benchmark
    public void benchmarkJte(Blackhole bh) throws IOException {
        try (var writer = new CountingBlackholeWriter(bh, globalCharCount)) {
            jteEngine.render("fortune.jte", Map.of("fortunes", this.fortunes), new WriterOutput(writer));
        }
    }

    /** Benchmark for HtmlFlow API */
    @Benchmark
    public void benchmarkHtmlFlow(Blackhole bh) {
        var result = HtmlFlow.view(view -> view
                .html().body().table()
                .of(table -> {
                    for (var fortune : this.fortunes) {
                        table.tr()
                                .td().text(String.valueOf(fortune.id())).__()
                                .td().text(fortune.message()).__()
                                .td().text(fortune.author()).__()
                                .__();
                    }
                }).__().__().__()
        ).render();
        globalCharCount.add(result.length());
        bh.consume(result);
    }

    /** Benchmark for standard StringBuilder fallback */
    @Benchmark
    public void benchmarkStringBuilder(Blackhole bh) {
        var builder = new StringBuilder(2048);
        builder.append("<html><body><table>");
        for (var fortune : this.fortunes) {
            builder.append("<tr><td>").append(fortune.id()).append("</td>")
                    .append("<td>").append(fortune.message()).append("</td>")
                    .append("<td>").append(fortune.author()).append("</td></tr>");
        }
        builder.append("</table></body></html>");
        globalCharCount.add(builder.length());
        bh.consume(builder);
    }

    /** Benchmark for Dom4j full DOM tree builder */
    @Benchmark
    public void benchmarkDom4j(Blackhole bh) throws Exception {
        var document = org.dom4j.DocumentHelper.createDocument();
        var html = document.addElement("html");
        var body = html.addElement("body");
        var table = body.addElement("table");

        for (var fortune : this.fortunes) {
            var tr = table.addElement("tr");
            tr.addElement("td").addText(String.valueOf(fortune.id()));
            tr.addElement("td").addText(fortune.message());
            tr.addElement("td").addText(fortune.author());
        }

        try (var writer = new CountingBlackholeWriter(bh, globalCharCount)) {
            var outputFormat = org.dom4j.io.OutputFormat.createCompactFormat();
            outputFormat.setSuppressDeclaration(true);
            var xmlWriter = new org.dom4j.io.XMLWriter(writer, outputFormat);
            xmlWriter.write(document);
        }
    }

    /** Benchmark for Jsoup DOM builder */
    @Benchmark
    public void benchmarkJsoup(Blackhole bh) {
        var doc = org.jsoup.nodes.Document.createShell("");
        doc.outputSettings().prettyPrint(false);
        var table = doc.body().appendElement("table");

        for (var fortune : this.fortunes) {
            var tr = table.appendElement("tr");
            tr.appendElement("td").text(String.valueOf(fortune.id()));
            tr.appendElement("td").text(fortune.message());
            tr.appendElement("td").text(fortune.author());
        }

        var result = doc.outerHtml();
        globalCharCount.add(result.length());
        bh.consume(result);
    }

    /** Benchmark for Kotlinx HTML DSL */
    @Benchmark
    public void benchmarkKotlinxHtml(Blackhole bh) throws IOException {
        try (var writer = new CountingBlackholeWriter(bh, globalCharCount)) {
            KotlinHtml.INSTANCE.renderFortunes(this.fortunes, writer);
        }
    }

    /** A custom Writer that feeds Blackhole and safely counts characters */
    private static class CountingBlackholeWriter extends Writer {
        private final Blackhole bh;
        private final LongAdder counter;

        /** Constructor for the CountingBlackholeWriter */
        public CountingBlackholeWriter(Blackhole bh, LongAdder counter) {
            this.bh = bh;
            this.counter = counter;
        }

        @Override
        public void write(char[] cbuf, int off, int len) {
            counter.add(len);
            bh.consume(cbuf);
        }

        @Override public void flush() {}
        @Override public void close() {}
    }

    /** Represents a fortune message entity */
    public record Fortune(
            /** Gets the id */
            int id,

            /** Gets the author's first name */
            String author,

            /** Gets the message */
            String message
    ) { }

    /** Run the benchmark programmatically */
    public static void main(String[] args) throws RunnerException {
        var opt = new OptionsBuilder()
                .include(HtmlBenchmark.class.getSimpleName())
                .addProfiler("gc")
                .build();
        new Runner(opt).run();
    }
}
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
                new Fortune(101, "A computer scientist is someone who fixes things that aren't broken."),
                new Fortune(102, "Feature: A bug with seniority."),
                new Fortune(103, "To err is human, but to really foul things up you need a computer."),
                new Fortune(104, "Programmers are tools for converting caffeine into code."),
                new Fortune(105, "There are 10 types of people: those who understand binary and those who don't."),
                new Fortune(106, "Debugging: Being the detective in a crime movie where you are also the murderer."),
                new Fortune(107, "It's not a bug, it's an undocumented feature."),
                new Fortune(108, "Weeks of coding can save you hours of planning."),
                new Fortune(109, "If at first you don’t succeed, call it version 1.0."),
                new Fortune(110, "Computers make very fast, very accurate mistakes.")
        );
        var codeResolver = new DirectoryCodeResolver(Path.of("src/main/resources"));
        this.jteEngine = TemplateEngine.create(codeResolver, ContentType.Html);

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
                                            td(fortune.message())
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
                    .append("<td>").append(fortune.message()).append("</td></tr>");
        }
        builder.append("</table></body></html>");
        globalCharCount.add(builder.length());
        bh.consume(builder);
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

    /** Run the benchmark programmatically */
    public static void main(String[] args) throws RunnerException {
        var opt = new OptionsBuilder()
                .include(HtmlBenchmark.class.getSimpleName())
                .addProfiler("gc")
                .build();
        new Runner(opt).run();
    }
}
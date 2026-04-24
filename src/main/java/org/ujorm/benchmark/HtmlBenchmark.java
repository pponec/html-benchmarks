package org.ujorm.benchmark;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.resolve.DirectoryCodeResolver;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.ujorm.tools.xml.config.HtmlConfig;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/** JMH benchmark for HTML generation performance */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class HtmlBenchmark {

    @Param({"SIMPLE_10", "SIMPLE_100", "SIMPLE_1000", "COMPLEX_100"})
    public Scenario scenario;

    private List<Fortune> fortunes;
    private List<String> menuItems;
    private boolean showPromo;
    private BenchmarkModel model;
    private TemplateEngine jteEngine;
    private HtmlConfig config;
    private final LongAdder globalCharCount = new LongAdder();

    /** Setup the testing data and engines */
    @Setup
    public void setup() {
        this.config = HtmlConfig.ofDefault();
        var baseFortunes = List.of(
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
        this.fortunes = createFortunes(baseFortunes, scenario);
        this.menuItems = List.of("Home", "Fortunes", "Authors", "Stats");
        this.showPromo = scenario == Scenario.COMPLEX_100;
        this.model = new BenchmarkModel(this.fortunes, this.menuItems, this.showPromo);
        var codeResolver = new DirectoryCodeResolver(Path.of("src/main/resources"));
        this.jteEngine = TemplateEngine.create(codeResolver, ContentType.Html);
        this.jteEngine.setTrimControlStructures(true);

        globalCharCount.reset();
        HtmlOutputEquivalenceVerifier.verifyEquivalentHtmlOutputs(
                this.fortunes, this.menuItems, this.showPromo, this.config, this.jteEngine, this.scenario.isComplex()
        );
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
        try (var writer = new CountingBlackholeWriter(bh, globalCharCount)) {
            UjormRenderer.render(model, scenario, config, writer);
        }
    }

    /** Benchmark for j2html pure Java API */
    @Benchmark
    public void benchmarkJ2html(Blackhole bh) throws IOException {
        try (var writer = new CountingBlackholeWriter(bh, globalCharCount)) {
            J2htmlRenderer.render(model, scenario, writer);
        }
    }

    /** Benchmark for JTE template engine */
    @Benchmark
    public void benchmarkJte(Blackhole bh) throws IOException {
        try (var writer = new CountingBlackholeWriter(bh, globalCharCount)) {
            JteRenderer.render(model, scenario, jteEngine, writer);
        }
    }

    /** Benchmark for HtmlFlow API */
    @Benchmark
    public void benchmarkHtmlFlow(Blackhole bh) {
        var result = HtmlFlowRenderer.render(model, scenario);
        globalCharCount.add(result.length());
        bh.consume(result);
    }

    /** Benchmark for standard StringBuilder fallback */
    @Benchmark
    public void benchmarkStringBuilder(Blackhole bh) {
        var result = SafeStringBuilderRenderer.render(model, scenario);
        globalCharCount.add(result.length());
        bh.consume(result);
    }

    /** Benchmark for Dom4j full DOM tree builder */
    @Benchmark
    public void benchmarkDom4j(Blackhole bh) throws Exception {
        try (var writer = new CountingBlackholeWriter(bh, globalCharCount)) {
            Dom4jRenderer.render(model, scenario, writer);
        }
    }

    /** Benchmark for Jsoup DOM builder */
    @Benchmark
    public void benchmarkJsoup(Blackhole bh) {
        var result = JsoupRenderer.render(model, scenario);
        globalCharCount.add(result.length());
        bh.consume(result);
    }

    /** Benchmark for Kotlinx HTML DSL */
    @Benchmark
    public void benchmarkKotlinxHtml(Blackhole bh) throws IOException {
        try (var writer = new CountingBlackholeWriter(bh, globalCharCount)) {
            KotlinxHtmlRenderer.render(model, scenario, writer);
        }
    }

    private static List<Fortune> createFortunes(List<Fortune> base, Scenario scenario) {
        var result = new java.util.ArrayList<Fortune>(scenario.rowCount);
        for (int i = 0; i < scenario.rowCount; i++) {
            var src = base.get(i % base.size());
            var id = src.id() * 1000 + i;
            var author = injectSpecialCharacters(src.author() + " #" + i, i);
            var message = injectSpecialCharacters(src.message() + " [row " + i + "]", i + 101);
            result.add(new Fortune(id, author, message));
        }
        return result;
    }

    /** Inject special HTML-sensitive symbols into approximately 5% of characters. */
    private static String injectSpecialCharacters(String input, int seed) {
        if (input.isBlank()) {
            return input;
        }
        var chars = input.toCharArray();
        var replacements = new char[]{'<', '>', '&', '"', '\''};
        int target = Math.max(1, chars.length / 20); // ~5%
        int step = Math.max(1, chars.length / target);
        int done = 0;
        for (int pos = step / 2; pos < chars.length && done < target; pos += step) {
            if (!Character.isWhitespace(chars[pos])) {
                chars[pos] = replacements[Math.floorMod(seed + done, replacements.length)];
                done++;
            }
        }
        return new String(chars);
    }

    public static List<String> tokensOf(String text) {
        return java.util.Arrays.stream(text.split(" "))
                .filter(token -> !token.isBlank())
                .limit(6)
                .toList();
    }

    public record BenchmarkModel(List<Fortune> fortunes, List<String> menuItems, boolean showPromo) {}

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

    public enum Scenario {
        SIMPLE_10(10, false),
        SIMPLE_100(100, false),
        SIMPLE_1000(1000, false),
        COMPLEX_100(100, true);

        final int rowCount;
        final boolean complexLayout;

        Scenario(int rowCount, boolean complexLayout) {
            this.rowCount = rowCount;
            this.complexLayout = complexLayout;
        }

        boolean isComplex() {
            return complexLayout;
        }
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
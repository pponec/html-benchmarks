package org.ujorm.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Builds a scenario-aware CSV report from JMH output. */
public final class BenchmarkReportGenerator {

    private static final Pattern BENCHMARK_LINE = Pattern.compile("^HtmlBenchmark\\.benchmark([A-Za-z0-9]+)(?::([a-zA-Z0-9._-]+))?.*$");
    private static final Pattern SCENARIO_PARAM = Pattern.compile("\\(scenario = ([^)]+)\\)");
    private static final Pattern SCORE_PATTERN = Pattern.compile("(thrpt|avgt)\\s+\\d+\\s+([0-9]+(?:[.,][0-9]+)?)\\b");
    private static final Pattern SCENARIO_TOKEN = Pattern.compile("^(SIMPLE_\\d+|COMPLEX_\\d+)$");

    private static final Map<String, String> ARTIFACT_BY_FRAMEWORK = Map.of(
            "UjormElement", "ujo-web",
            "J2html", "j2html",
            "HtmlFlow", "htmlflow",
            "Jte", "jte",
            "Dom4j", "dom4j",
            "Jsoup", "jsoup",
            "KotlinxHtml", "kotlinx-html-jvm",
            "StringBuilder", ""
    );
    private static final List<String> ALL_SCENARIOS = List.of(
            "SIMPLE_10",
            "SIMPLE_100",
            "SIMPLE_1000",
            "COMPLEX_100"
    );

    private BenchmarkReportGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: BenchmarkReportGenerator <raw-log-path> <csv-output-path>");
        }
        var rawLog = Path.of(args[0]);
        var csvFile = Path.of(args[1]);
        generate(rawLog, csvFile);
    }

    static void generate(Path rawLog, Path csvFile) throws Exception {
        var metrics = parseMetrics(rawLog);
        var sizes = resolveJarSizes();

        var scenarios = new ArrayList<>(ALL_SCENARIOS);
        for (var key : metrics.keySet()) {
            if (!scenarios.contains(key.scenario())) {
                scenarios.add(key.scenario());
            }
        }
        scenarios.sort(scenarioOrder());

        var frameworks = new ArrayList<String>(ARTIFACT_BY_FRAMEWORK.keySet());
        frameworks.sort(frameworkOrder());

        var lines = new ArrayList<String>();
        var header = new ArrayList<String>();
        header.add("Framework");
        header.add("JAR Size [kB]");
        for (var scenario : scenarios) {
            header.add(scenario + " Throughput [ops/s]");
            header.add(scenario + " Allocation [B/op]");
        }
        lines.add(String.join("|", header));

        for (var framework : frameworks) {
            var row = new ArrayList<String>();
            row.add(framework);
            row.add(sizes.getOrDefault(framework, "?"));

            for (var scenario : scenarios) {
                var metric = metrics.get(new Key(framework, scenario));
                row.add(metric != null && metric.throughput() != null ? metric.throughput() : "0");
                row.add(metric != null && metric.allocation() != null ? metric.allocation() : "0");
            }
            lines.add(String.join("|", row));
        }
        Files.write(csvFile, lines);
    }

    private static Map<Key, Metric> parseMetrics(Path rawLog) throws IOException {
        var result = new LinkedHashMap<Key, Metric>();
        for (var line : Files.readAllLines(rawLog)) {
            var benchmarkMatcher = BENCHMARK_LINE.matcher(line);
            if (!benchmarkMatcher.matches()) {
                continue;
            }

            var framework = benchmarkMatcher.group(1);
            var metricName = benchmarkMatcher.group(2);
            var scenario = parseScenario(line);
            var score = parseScore(line);
            if (score == null) {
                continue;
            }

            var key = new Key(framework, scenario);
            var current = result.getOrDefault(key, new Metric(null, null));

            if (metricName == null || metricName.isBlank()) {
                result.put(key, new Metric(score, current.allocation()));
            } else if ("gc.alloc.rate.norm".equals(metricName)) {
                result.put(key, new Metric(current.throughput(), score));
            }
        }
        return result;
    }

    private static String parseScenario(String line) {
        var paramMatcher = SCENARIO_PARAM.matcher(line);
        if (paramMatcher.find()) {
            return paramMatcher.group(1);
        }
        var tokens = line.trim().split("\\s+");
        for (var token : tokens) {
            if (SCENARIO_TOKEN.matcher(token).matches()) {
                return token;
            }
        }
        return "DEFAULT";
    }

    private static String parseScore(String line) {
        var scoreMatcher = SCORE_PATTERN.matcher(line);
        return scoreMatcher.find() ? scoreMatcher.group(2) : null;
    }

    private static Map<String, String> resolveJarSizes() throws Exception {
        var sizes = new HashMap<String, String>();
        for (var entry : ARTIFACT_BY_FRAMEWORK.entrySet()) {
            var framework = entry.getKey();
            var artifactId = entry.getValue();
            if (artifactId.isBlank()) {
                sizes.put(framework, "0");
            } else {
                sizes.put(framework, String.valueOf(resolveArtifactSizeKb(artifactId)));
            }
        }
        return sizes;
    }

    private static long resolveArtifactSizeKb(String artifactId) throws Exception {
        var tempDirName = "temp-deps-" + UUID.randomUUID().toString().substring(0, 8);
        var tempDir = Path.of(tempDirName);

        var process = new ProcessBuilder(
                "./mvnw",
                "dependency:copy-dependencies",
                "-DincludeArtifactIds=" + artifactId,
                "-DoutputDirectory=" + tempDirName,
                "-Dmdep.useRepositoryLayout=false",
                "-q"
        ).inheritIO().start();

        var exitCode = process.waitFor();
        if (exitCode != 0 || !Files.exists(tempDir)) {
            deleteQuietly(tempDir);
            return 0L;
        }

        long bytes;
        try (var walk = Files.walk(tempDir)) {
            bytes = walk
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        }
        deleteQuietly(tempDir);
        return Math.round(bytes / 1024.0);
    }

    private static void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(item -> {
                        try {
                            Files.deleteIfExists(item);
                        } catch (IOException ignored) {
                            // Ignore cleanup failures for temp files.
                        }
                    });
        } catch (IOException ignored) {
            // Ignore cleanup failures for temp files.
        }
    }

    private static Comparator<String> scenarioOrder() {
        return Comparator
                .comparingInt(BenchmarkReportGenerator::scenarioRank)
                .thenComparing(Comparator.naturalOrder());
    }

    private static int scenarioRank(String scenario) {
        if (scenario.startsWith("SIMPLE_")) {
            var suffix = scenario.substring("SIMPLE_".length());
            try {
                return Integer.parseInt(suffix);
            } catch (NumberFormatException ignored) {
                return 1000;
            }
        }
        if (scenario.startsWith("COMPLEX_")) {
            return 10_000;
        }
        return 30_000;
    }

    private static Comparator<String> frameworkOrder() {
        var collator = Collator.getInstance(Locale.ROOT);
        return collator::compare;
    }

    private record Key(String framework, String scenario) {
    }

    private record Metric(String throughput, String allocation) {
    }
}

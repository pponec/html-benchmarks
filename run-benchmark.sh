#!/bin/sh

set -e

# Usage:
#   ./run-benchmark.sh
#     Runs the full benchmark suite (all frameworks, all scenarios, default JMH settings).
#
#   ./run-benchmark.sh test
#     Runs a fast smoke benchmark intended to quickly validate CSV generation/format.
#     Runs full benchmark/scenario matrix with reduced JMH settings (single iteration).
#     Special characters are included in SIMPLE and COMPLEX data by default.
#
# Define filenames
RAW_LOG="jmh-raw-output-$RANDOM.log"
CSV_FILE="html-benchmark-results.csv"

# Silence the "sun.misc.Unsafe" warning for internal Maven processes (like Guice)
export MAVEN_OPTS="--sun-misc-unsafe-memory-access=allow"

# 1. Compile and build the project
echo "Compiling the project..."
./mvnw clean package -q

# 2. Run the benchmark, tee the output to both the console and the temporary log file
if [ "$1" = "test" ]; then
    echo "Running JMH benchmark in TEST mode..."
    java --sun-misc-unsafe-memory-access=allow -jar target/benchmarks.jar \
        "HtmlBenchmark.benchmark.*" \
        -p scenario=SIMPLE_10,SIMPLE_100,SIMPLE_1000,COMPLEX_100 \
        -wi 0 \
        -i 1 \
        -r 1s \
        -f 1 \
        -prof gc \
        -jvmArgsAppend "--sun-misc-unsafe-memory-access=allow" | tee "$RAW_LOG"
else
    echo "Running FULL JMH benchmark..."
    java --sun-misc-unsafe-memory-access=allow -jar target/benchmarks.jar \
        -prof gc \
        -jvmArgsAppend "--sun-misc-unsafe-memory-access=allow" | tee "$RAW_LOG"
fi

# 3. Build CSV summary table in Java (scenario-aware and robust to JMH output formats)
echo "Building the CSV summary table..."
java -cp target/classes org.ujorm.benchmark.BenchmarkReportGenerator "$RAW_LOG" "$CSV_FILE"

# 4. Display the final result
echo "--------------------------------------------------------"
echo "Done! The resulting table ($CSV_FILE):"
cat "$CSV_FILE"
echo "--------------------------------------------------------"

# 5. Clean up the temporary log file
rm -f "$RAW_LOG"
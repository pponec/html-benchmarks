#!/bin/sh

# Define filenames using variables
RAW_LOG="jmh-raw-output-$RANDOM.log"
CSV_FILE="html-benchmark-results.csv"

# 1. Compile and build the project
./mvnw clean package

# 2. Run the benchmark, tee the output to both the console and the temporary log file
echo "Running JMH benchmark..."
java -jar target/benchmarks.jar -prof gc | tee "$RAW_LOG"

# 3. Process the log using AWK to generate the CSV format
echo "Building the CSV summary table..."

awk '
BEGIN {
    # Print the table header
    print "Framework|Throughput [ops/s]|Allocation [B/op]"
}
/^HtmlBenchmark\.benchmark/ {
    # Process lines starting with the benchmark method name
    # Example 1: HtmlBenchmark.benchmarkHtmlFlow
    # Example 2: HtmlBenchmark.benchmarkHtmlFlow:gc.alloc.rate.norm

    # Split the first column by colon to separate the method name from the metric
    split($1, parts, ":")
    method_part = parts[1]
    metric = parts[2]

    # Split the method_part by dot to get the actual benchmark name
    # (e.g., separating "HtmlBenchmark" and "benchmarkHtmlFlow")
    split(method_part, subparts, ".")
    framework = subparts[2]

    # Remove the "benchmark" prefix to get a clean framework name
    sub(/^benchmark/, "", framework)

    # Store the parsed values into associative arrays ($4 is the Score)
    score = $4

    if (metric == "") {
        # Main throughput metric (ops/s)
        ops[framework] = score
    } else if (metric == "gc.alloc.rate.norm") {
        # Memory allocation metric (B/op)
        mem[framework] = score
    }
}
END {
    # Print the paired results for each framework
    for (f in ops) {
        # If memory data is missing for any reason, use a question mark as a fallback
        memory = (mem[f] != "") ? mem[f] : "?"
        print f "|" ops[f] "|" memory
    }
}' "$RAW_LOG" > "$CSV_FILE"

# 4. Display the final result
echo "--------------------------------------------------------"
echo "Done! The resulting table ($CSV_FILE):"
cat "$CSV_FILE"
echo "--------------------------------------------------------"

# 5. Clean up the temporary log file
rm -f "$RAW_LOG"
#!/bin/sh

# Define filenames and variables
RAW_LOG="jmh-raw-output-$RANDOM.log"
CSV_FILE="html-benchmark-results.csv"

# 1. Compile and build the project
echo "Compiling the project..."
./mvnw clean package -q

# --- FUNCTION FOR JAR SIZE (in kB) ---
# Uses maven-dependency-plugin to copy a specific artifact and its transitive dependencies
# into an isolated temporary folder, measures the folder size, and cleans up.
get_framework_size_kb() {
    local artifact_id=$1
    local temp_dir="temp-deps-$RANDOM"

    # Run Maven to copy dependencies for the given artifact into a temporary directory
    ./mvnw dependency:copy-dependencies \
        -DincludeArtifactIds="$artifact_id" \
        -DoutputDirectory="$temp_dir" \
        -Dmdep.useRepositoryLayout=false \
        -q

    if [ -d "$temp_dir" ]; then
        # Calculate size in bytes using 'du', convert to kB (rounded to integer)
        local size_bytes=$(du -sb "$temp_dir" | awk '{print $1}')
        awk -v bytes="$size_bytes" 'BEGIN { printf "%.0f", bytes / 1024 }'
        rm -rf "$temp_dir"
    else
        echo "0"
    fi
}

echo "Calculating pure framework sizes in kB (without JMH overhead)..."
# Map framework names (from JMH output) to their Maven Artifact IDs
# Note: Ensure these match the artifactIds in your pom.xml
SIZE_UJORM=$(get_framework_size_kb "ujo-web")
SIZE_J2HTML=$(get_framework_size_kb "j2html")
SIZE_HTMLFLOW=$(get_framework_size_kb "htmlflow")
SIZE_JTE=$(get_framework_size_kb "jte")
SIZE_STRINGBUILDER="0" # Native JDK, no external dependencies

# Export sizes so AWK can read them from environment variables
export SIZE_UjormElement=$SIZE_UJORM
export SIZE_J2html=$SIZE_J2HTML
export SIZE_HtmlFlow=$SIZE_HTMLFLOW
export SIZE_Jte=$SIZE_JTE
export SIZE_StringBuilder=$SIZE_STRINGBUILDER

# 2. Run the benchmark, tee the output to both the console and the temporary log file
echo "Running JMH benchmark..."
java -jar target/benchmarks.jar -prof gc | tee "$RAW_LOG"

# 3. Process the log using AWK to generate the CSV format (now with JAR Size in kB)
echo "Building the CSV summary table..."

awk '
BEGIN {
    # Print the table header
    print "Framework|Throughput [ops/s]|Allocation [B/op]|JAR Size [kB]"
}
/^HtmlBenchmark\.benchmark/ {
    # Split the first column by colon to separate method name and metric
    split($1, parts, ":")
    method_part = parts[1]
    metric = parts[2]

    # Split the method_part by dot to get the actual benchmark name
    split(method_part, subparts, ".")
    framework = subparts[2]

    # Remove the "benchmark" prefix
    sub(/^benchmark/, "", framework)

    # Store the parsed values
    score = $4

    if (metric == "") {
        ops[framework] = score
    } else if (metric == "gc.alloc.rate.norm") {
        mem[framework] = score
    }
}
END {
    # Print the paired results for each framework including the size from ENV
    for (f in ops) {
        memory = (mem[f] != "") ? mem[f] : "?"

        # Fetch the pre-calculated size from environment variables
        env_var_name = "SIZE_" f
        jar_size = ENVIRON[env_var_name]
        if (jar_size == "") jar_size = "?"

        print f "|" ops[f] "|" memory "|" jar_size
    }
}' "$RAW_LOG" > "$CSV_FILE"

# 4. Display the final result
echo "--------------------------------------------------------"
echo "Done! The resulting table ($CSV_FILE):"
cat "$CSV_FILE"
echo "--------------------------------------------------------"

# 5. Clean up the temporary log file
rm -f "$RAW_LOG"
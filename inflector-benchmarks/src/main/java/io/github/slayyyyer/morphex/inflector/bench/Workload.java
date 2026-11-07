package io.github.slayyyyer.morphex.inflector.bench;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fixed benchmark inputs, loaded from the versioned classpath workload and never sampled at run time. */
public final class Workload {

    public static final String RESOURCE = "/benchmark-workload-v1.tsv";
    public static final List<String> CATEGORIES =
            List.of("lexical", "compacted", "unknown", "uppercase", "compound", "unsupported");

    private final Map<String, String[]> byCategory;
    private final String[] all;

    private Workload(Map<String, String[]> byCategory) {
        this.byCategory = Map.copyOf(byCategory);
        List<String> flattened = new ArrayList<>();
        for (String category : CATEGORIES) {
            flattened.addAll(List.of(byCategory.get(category)));
        }
        this.all = flattened.toArray(new String[0]);
    }

    public static Workload load() {
        InputStream in = Workload.class.getResourceAsStream(RESOURCE);
        if (in == null) {
            throw new IllegalStateException("missing benchmark workload resource " + RESOURCE);
        }
        Map<String, List<String>> collected = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            collected.put(category, new ArrayList<>());
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean header = false;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.split("\t", -1);
                if (columns.length < 2) {
                    throw new IllegalStateException("malformed workload row: " + line);
                }
                if (!header) {
                    header = true;
                    continue;
                }
                List<String> bucket = collected.get(columns[0]);
                if (bucket == null) {
                    throw new IllegalStateException("unknown workload category: " + columns[0]);
                }
                bucket.add(columns[1]);
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read benchmark workload", e);
        }
        Map<String, String[]> byCategory = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : collected.entrySet()) {
            if (entry.getValue().isEmpty()) {
                throw new IllegalStateException("empty workload category: " + entry.getKey());
            }
            byCategory.put(entry.getKey(), entry.getValue().toArray(new String[0]));
        }
        return new Workload(byCategory);
    }

    public String[] category(String name) {
        String[] values = byCategory.get(name);
        if (values == null) {
            throw new IllegalArgumentException("unknown workload category: " + name);
        }
        return values;
    }

    public String[] all() {
        return all;
    }
}

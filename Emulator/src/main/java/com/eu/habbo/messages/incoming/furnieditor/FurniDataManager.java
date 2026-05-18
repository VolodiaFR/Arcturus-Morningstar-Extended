package com.eu.habbo.messages.incoming.furnieditor;

import com.eu.habbo.Emulator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Manages reading and writing of FurnitureData entries.
 *
 * Accepts both legacy single-file layouts (FurnitureData.json) and the split
 * directory layout introduced by the split-aware loader on the Nitro V3 side:
 *
 *   <base>/
 *     manifest.json5         OPTIONAL  { "tiers": ["core", "custom", "seasonal"] }
 *     core/manifest.json5    REQUIRED  { "files": ["floor-001.json5", ...] }
 *     core/*.json5
 *     custom/manifest.json5  OPTIONAL
 *     seasonal/manifest.json5 OPTIONAL
 *
 * The path is resolved from the emulator config:
 *
 *   furni.editor.renderer.config.path  -> renderer-config.json (read for the
 *                                         furnidata.url value)
 *   furni.editor.asset.base.path       -> filesystem base used to derive the
 *                                         local path from an http(s) URL
 */
public class FurniDataManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(FurniDataManager.class);

    private static final List<String> DEFAULT_TIERS = Arrays.asList("core", "custom", "seasonal");
    private static final List<String> MANIFEST_NAMES = Arrays.asList("manifest.json5", "manifest.json");
    private static final List<String> SECTIONS = Arrays.asList("roomitemtypes", "wallitemtypes");

    /**
     * Get the JSON string for a specific item.
     * Returns "{}" if not found or on error.
     */
    public static String getItemJson(int itemId) {
        try {
            ResolvedSource source = resolveSource();
            if (source == null) return "{}";

            if (source.directory) {
                return findItemInSplitDir(source.path, itemId);
            }

            if (!Files.exists(source.path)) return "{}";

            String content = readJson5(source.path);
            return findItemInRoot(JsonParser.parseString(content).getAsJsonObject(), itemId);
        } catch (Exception e) {
            LOGGER.warn("Failed to read FurnitureData for item " + itemId, e);
        }

        return "{}";
    }

    private static String findItemInRoot(JsonObject root, int itemId) {
        for (String section : SECTIONS) {
            if (!root.has(section)) continue;
            JsonObject sectionObj = root.getAsJsonObject(section);
            if (!sectionObj.has("furnitype")) continue;
            JsonArray types = sectionObj.getAsJsonArray("furnitype");

            for (JsonElement el : types) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("id") && obj.get("id").getAsInt() == itemId) {
                    return obj.toString();
                }
            }
        }
        return null;
    }

    /**
     * Walk the split directory layout looking for an item by id.
     * Later tiers (custom, then seasonal) override earlier ones.
     */
    private static String findItemInSplitDir(Path baseDir, int itemId) {
        if (!Files.isDirectory(baseDir)) return "{}";

        List<String> tiers = readTiersManifest(baseDir);
        String found = null;

        for (String tier : tiers) {
            Path tierDir = baseDir.resolve(tier);
            if (!Files.isDirectory(tierDir)) continue;

            List<String> files = readFilesManifest(tierDir);
            for (String fileName : files) {
                Path file = tierDir.resolve(fileName);
                if (!Files.exists(file)) continue;

                try {
                    String content = readJson5(file);
                    JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
                    String match = findItemInRoot(obj, itemId);
                    if (match != null) found = match;
                } catch (Exception e) {
                    LOGGER.warn("Failed to parse split gamedata file " + file, e);
                }
            }
        }

        return found != null ? found : "{}";
    }

    @SuppressWarnings("unchecked")
    private static List<String> readTiersManifest(Path baseDir) {
        Path manifest = firstExisting(baseDir, MANIFEST_NAMES);
        if (manifest == null) return DEFAULT_TIERS;

        try {
            String content = readJson5(manifest);
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            if (obj.has("tiers") && obj.get("tiers").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("tiers");
                List<String> out = new java.util.ArrayList<>();
                for (JsonElement el : arr) out.add(el.getAsString());
                if (!out.isEmpty()) return out;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read root manifest " + manifest + ", falling back to default tiers", e);
        }
        return DEFAULT_TIERS;
    }

    private static List<String> readFilesManifest(Path tierDir) {
        Path manifest = firstExisting(tierDir, MANIFEST_NAMES);
        if (manifest == null) return java.util.Collections.emptyList();

        try {
            String content = readJson5(manifest);
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            if (obj.has("files") && obj.get("files").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("files");
                List<String> out = new java.util.ArrayList<>();
                for (JsonElement el : arr) out.add(el.getAsString());
                return out;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read tier manifest " + manifest, e);
        }
        return java.util.Collections.emptyList();
    }

    private static Path firstExisting(Path dir, List<String> names) {
        for (String name : names) {
            Path p = dir.resolve(name);
            if (Files.exists(p)) return p;
        }
        return null;
    }

    /**
     * Read a JSON or JSON5 file. Strips line and block comments and trailing
     * commas so Gson can parse the result. String contents are preserved
     * verbatim; comments embedded inside strings are not removed.
     */
    private static String readJson5(Path path) throws IOException {
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        return stripJson5(raw);
    }

    static String stripJson5(String content) {
        if (content == null || content.isEmpty()) return content;

        StringBuilder out = new StringBuilder(content.length());
        int i = 0;
        int len = content.length();
        boolean inString = false;
        char stringChar = 0;
        boolean escape = false;

        while (i < len) {
            char c = content.charAt(i);

            if (inString) {
                out.append(c);
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == stringChar) {
                    inString = false;
                }
                i++;
                continue;
            }

            if (c == '"' || c == '\'') {
                inString = true;
                stringChar = c;
                out.append(c);
                i++;
                continue;
            }

            if (c == '/' && i + 1 < len) {
                char next = content.charAt(i + 1);
                if (next == '/') {
                    int eol = content.indexOf('\n', i + 2);
                    if (eol < 0) { i = len; break; }
                    i = eol;
                    continue;
                }
                if (next == '*') {
                    int end = content.indexOf("*/", i + 2);
                    if (end < 0) { i = len; break; }
                    i = end + 2;
                    continue;
                }
            }

            out.append(c);
            i++;
        }

        String stripped = out.toString();
        // Remove trailing commas before } or ]
        stripped = stripped.replaceAll(",(\\s*[}\\]])", "$1");
        return stripped;
    }

    /**
     * Represents the resolved location of the furnidata source: either a single
     * file or a directory in split-layout mode.
     */
    private static class ResolvedSource {
        final Path path;
        final boolean directory;

        ResolvedSource(Path path, boolean directory) {
            this.path = path;
            this.directory = directory;
        }
    }

    /**
     * Resolve the location of the furnidata source. Returns null if no
     * candidate can be found.
     */
    private static ResolvedSource resolveSource() {
        try {
            String configPath = Emulator.getConfig().getValue("furni.editor.renderer.config.path", "");

            if (configPath.isEmpty()) {
                Path fallback = fallbackToBasePath();
                return fallback != null ? new ResolvedSource(fallback, Files.isDirectory(fallback)) : null;
            }

            Path rendererConfig = Paths.get(configPath);
            if (!Files.exists(rendererConfig)) return null;

            String rendererContent = readJson5(rendererConfig);
            JsonObject rendererObj = JsonParser.parseString(rendererContent).getAsJsonObject();

            if (!rendererObj.has("furnidata.url")) return null;

            String furniUrl = rendererObj.get("furnidata.url").getAsString();

            if (furniUrl.contains("${")) {
                Path fallback = fallbackToBasePath();
                return fallback != null ? new ResolvedSource(fallback, Files.isDirectory(fallback)) : null;
            }

            // Strip query string and fragment (e.g. ?v=123 or #anchor)
            String cleanUrl = furniUrl;
            int q = cleanUrl.indexOf('?');
            if (q >= 0) cleanUrl = cleanUrl.substring(0, q);
            int h = cleanUrl.indexOf('#');
            if (h >= 0) cleanUrl = cleanUrl.substring(0, h);

            boolean splitMode = cleanUrl.endsWith("/");

            // Local file path (not http) — return as-is, the caller will check
            // whether it points at a file or a directory.
            if (!cleanUrl.startsWith("http")) {
                Path local = Paths.get(cleanUrl);
                return new ResolvedSource(local, splitMode || Files.isDirectory(local));
            }

            String basePath = Emulator.getConfig().getValue("furni.editor.asset.base.path", "");
            if (basePath.isEmpty()) return null;

            if (splitMode) {
                // Derive the directory name from the URL: take the last non-empty
                // segment before the trailing slash. e.g. https://x/y/furnidata/ -> "furnidata"
                String trimmed = cleanUrl.endsWith("/") ? cleanUrl.substring(0, cleanUrl.length() - 1) : cleanUrl;
                String dirName = trimmed.substring(trimmed.lastIndexOf('/') + 1);
                Path candidate = Paths.get(basePath, dirName);
                return new ResolvedSource(candidate, true);
            }

            String filename = cleanUrl.substring(cleanUrl.lastIndexOf('/') + 1);
            Path candidate = Paths.get(basePath, filename);
            return new ResolvedSource(candidate, false);
        } catch (Exception e) {
            LOGGER.warn("Failed to resolve FurnitureData source", e);
        }

        return null;
    }

    private static Path fallbackToBasePath() {
        String basePath = Emulator.getConfig().getValue("furni.editor.asset.base.path", "");
        if (basePath.isEmpty()) return null;
        Path dir = Paths.get(basePath);
        // Prefer the split layout if it exists, then the legacy file.
        Path splitCandidate = dir.resolve("furnidata");
        if (Files.isDirectory(splitCandidate)) return splitCandidate;
        Path legacy = dir.resolve("FurnitureData.json");
        if (Files.exists(legacy)) return legacy;
        return null;
    }
}

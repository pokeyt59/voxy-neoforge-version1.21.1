package me.cortex.voxy.client.core.gl.shader;


import net.caffeinemc.mods.sodium.client.gl.shader.ShaderConstants;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShaderLoader {
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*#import\\s*<\\s*voxy\\s*:\\s*([^>]+?)\\s*>\\s*$", Pattern.MULTILINE);

    public static String parse(String id) {
        // NeoForge module isolation prevents Sodium's ShaderLoader from reading voxy resources,
        // so we resolve voxy:* imports ourselves before handing the source to Sodium's parser.
        // Also strip every #version line so we can prepend a single canonical one — the per-file
        // versions in voxy's shaders would otherwise collide with our 460 core preamble.
        String source = loadAndFlatten(stripNamespace(id), new HashSet<>())
                .replaceAll("(?m)^\\s*#version\\b.*$", "");
        return "#version 460 core\n" + ShaderParser.parseShader(source, ShaderConstants.builder().build()).replaceAll("\r\n", "\n");
    }

    private static String stripNamespace(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    private static String loadAndFlatten(String path, Set<String> visited) {
        if (!visited.add(path)) {
            return "";
        }
        String resourcePath = "/assets/voxy/shaders/" + path;
        String src;
        try (InputStream is = ShaderLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Shader not found: " + resourcePath);
            }
            src = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader: " + resourcePath, e);
        }

        Matcher m = IMPORT_PATTERN.matcher(src);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(loadAndFlatten(m.group(1), visited)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}

package com.thanh.workhub.vocabulary.internal.api.dto;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

public record CharacterDecomposition(String character, String hanViet, String layoutVi, List<Component> components,
        String mnemonicVi) {

    private static final Map<String, CharacterDecomposition> ENTRIES = loadEntries();

    public record Component(String character, String hanViet, String meaningVi) {
    }

    public String getComponentsVi() {
        return components.stream()
                .map(part -> part.character() + " (" + part.meaningVi() + ")")
                .collect(Collectors.joining(" + "));
    }

    /** Combined text for clients using the original response format. */
    public String getNoteVi() {
        return layoutVi + " · " + getComponentsVi() + ". " + mnemonicVi;
    }

    static List<CharacterDecomposition> forWord(String word) {
        return word.codePoints()
                .mapToObj(Character::toString)
                .map(ENTRIES::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private static Map<String, CharacterDecomposition> loadEntries() {
        var properties = new Properties();
        var stream = CharacterDecomposition.class.getResourceAsStream("/vocabulary/decomposition.properties");
        try (var reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
        var entries = new HashMap<String, CharacterDecomposition>();
        for (String character : properties.stringPropertyNames()) {
            String[] fields = properties.getProperty(character).split("\\|", -1);
            if (fields.length != 4 || Arrays.stream(fields).anyMatch(String::isBlank)) {
                throw new IllegalStateException("Invalid decomposition for " + character);
            }
            var components = Arrays.stream(fields[2].split(";", -1)).map(value -> {
                String[] part = value.split(":", 3);
                if (part.length != 3 || part[0].isBlank() || part[2].isBlank()) {
                    throw new IllegalStateException("Invalid component for " + character);
                }
                return new Component(part[0], part[1].isEmpty() ? null : part[1], part[2]);
            }).toList();
            entries.put(character, new CharacterDecomposition(character, fields[0], fields[1], components, fields[3]));
        }
        return Map.copyOf(entries);
    }
}

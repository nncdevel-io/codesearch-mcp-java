package io.nncdevel.codesearch.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RipgrepJsonTest {

    @Test
    void parsesSingleMatch() {
        String json =
                """
        {"type":"begin","data":{"path":{"text":"src/A.java"}}}
        {"type":"match","data":{"path":{"text":"src/A.java"},"lines":{"text":"class A {}\\n"},"line_number":42}}
        {"type":"end","data":{"path":{"text":"src/A.java"}}}
        {"type":"summary","data":{}}
        """;
        List<RgMatch> matches = RipgrepJson.parseMatches(json, 0, 0);
        assertEquals(1, matches.size());
        RgMatch m = matches.get(0);
        assertEquals("src/A.java", m.file());
        assertEquals(42, m.lineNumber());
        assertEquals("class A {}", m.lineContent());
        assertTrue(m.contextBefore().isEmpty());
        assertTrue(m.contextAfter().isEmpty());
    }

    @Test
    void associatesContextByProximity() {
        String json =
                """
        {"type":"context","data":{"path":{"text":"f.txt"},"lines":{"text":"before\\n"},"line_number":9}}
        {"type":"match","data":{"path":{"text":"f.txt"},"lines":{"text":"hit\\n"},"line_number":10}}
        {"type":"context","data":{"path":{"text":"f.txt"},"lines":{"text":"after\\n"},"line_number":11}}
        """;
        List<RgMatch> matches = RipgrepJson.parseMatches(json, 1, 1);
        assertEquals(1, matches.size());
        RgMatch m = matches.get(0);
        assertEquals(
                List.of(9), m.contextBefore().stream().map(RgLine::lineNumber).toList());
        assertEquals("before", m.contextBefore().get(0).content());
        assertEquals(
                List.of(11), m.contextAfter().stream().map(RgLine::lineNumber).toList());
        assertEquals("after", m.contextAfter().get(0).content());
    }

    @Test
    void toleratesBlankAndNonJsonLines() {
        String json =
                "\n{\"type\":\"match\",\"data\":{\"path\":{\"text\":\"a\"},\"lines\":{\"text\":\"x\"},\"line_number\":1}}\nnot-json\n";
        List<RgMatch> matches = RipgrepJson.parseMatches(json, 0, 0);
        assertEquals(1, matches.size());
    }

    @Test
    void emptyOutputYieldsNoMatches() {
        assertTrue(RipgrepJson.parseMatches("", 0, 0).isEmpty());
    }
}

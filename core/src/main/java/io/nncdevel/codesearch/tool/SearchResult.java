package io.nncdevel.codesearch.tool;

import java.util.List;

/** Result of {@code search_code}, one variant per {@link OutputMode} (spec §4.1.2–§4.1.4). */
public sealed interface SearchResult permits SearchResult.Content, SearchResult.Files, SearchResult.Count {

    /**
     * Content-mode result.
     *
     * @param matches the matching lines, capped at the requested maximum
     * @param truncated whether matches were dropped to honor the cap
     * @param totalMatches the number of matches returned
     */
    record Content(List<SearchMatch> matches, boolean truncated, int totalMatches) implements SearchResult {
        /** Defensively copies the match list. */
        public Content {
            matches = List.copyOf(matches);
        }
    }

    /**
     * Files-with-matches-mode result.
     *
     * @param files the files that contained a match, capped at the requested maximum
     * @param truncated whether files were dropped to honor the cap
     */
    record Files(List<FileHit> files, boolean truncated) implements SearchResult {
        /** Defensively copies the file list. */
        public Files {
            files = List.copyOf(files);
        }
    }

    /**
     * Count-mode result.
     *
     * @param files the per-file match counts, capped at the requested maximum
     * @param truncated whether files were dropped to honor the cap
     */
    record Count(List<CountHit> files, boolean truncated) implements SearchResult {
        /** Defensively copies the file list. */
        public Count {
            files = List.copyOf(files);
        }
    }
}

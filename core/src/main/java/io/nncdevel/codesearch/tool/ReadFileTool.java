package io.nncdevel.codesearch.tool;

import io.nncdevel.codesearch.config.RepositoryConfig;
import io.nncdevel.codesearch.error.ErrorCode;
import io.nncdevel.codesearch.error.ToolException;
import io.nncdevel.codesearch.repo.RepositoryManager;
import io.nncdevel.codesearch.util.GitUrl;
import io.nncdevel.codesearch.util.PathSafe;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Implements {@code read_file}: reads a line range from a text file without spawning a process
 * (spec §4.4). Enforces a 10&nbsp;MB size limit and rejects binary files.
 */
public final class ReadFileTool {

    /** Maximum readable file size in bytes (spec §4.4.4). */
    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private final RepositoryManager repositories;

    /**
     * Constructs the tool.
     *
     * @param context the shared tool collaborators
     */
    public ReadFileTool(ToolContext context) {
        this.repositories = context.repositories();
    }

    /**
     * Reads {@code numLines} lines starting at {@code startLine} from {@code filePath}.
     *
     * @param repository the repository id
     * @param filePath the repository-relative file path
     * @param startLine the 1-based first line to read
     * @param numLines the maximum number of lines to read
     * @return the formatted content and its actual range
     * @throws ToolException with {@link ErrorCode#REPO_NOT_FOUND}, {@link ErrorCode#REPO_NOT_READY},
     *     {@link ErrorCode#INVALID_PATH}, {@link ErrorCode#PATH_NOT_FOUND}, {@link
     *     ErrorCode#FILE_TOO_LARGE}, or {@link ErrorCode#FILE_BINARY}
     */
    public ReadFileResult read(String repository, String filePath, int startLine, int numLines) {
        RepositoryConfig repo = repositories.require(repository);
        Path workspace = repositories.requireReadyWorkspace(repository);
        Path file = PathSafe.resolveWithin(workspace, filePath);
        if (!Files.isRegularFile(file)) {
            throw new ToolException(ErrorCode.PATH_NOT_FOUND, "Path is not a regular file", Map.of("path", filePath));
        }

        long size;
        byte[] bytes;
        try {
            size = Files.size(file);
            if (size > MAX_FILE_BYTES) {
                throw new ToolException(
                        ErrorCode.FILE_TOO_LARGE,
                        "File exceeds the 10MB read limit",
                        Map.of("path", filePath, "size_bytes", size));
            }
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ToolException(
                    ErrorCode.PATH_NOT_FOUND, "Path does not exist in the repository", Map.of("path", filePath));
        }

        String text = decodeTextOrThrow(bytes, filePath);
        List<String> allLines = text.lines().toList();
        int totalLines = allLines.size();

        int actualStart = Math.max(startLine, 1);
        int actualEnd = Math.min(actualStart + numLines - 1, totalLines);
        List<String> slice = actualStart > totalLines ? List.of() : allLines.subList(actualStart - 1, actualEnd);
        if (actualStart > totalLines) {
            actualEnd = actualStart - 1;
        }

        String content = LineFormatter.format(slice, actualStart, actualEnd);
        String gitUrl = actualEnd > actualStart
                ? GitUrl.range(repo.hostingBaseUrl(), repo.hosting(), repo.branch(), filePath, actualStart, actualEnd)
                : GitUrl.singleLine(repo.hostingBaseUrl(), repo.hosting(), repo.branch(), filePath, actualStart);

        return new ReadFileResult(repository, filePath, actualStart, actualEnd, totalLines, content, gitUrl);
    }

    private static String decodeTextOrThrow(byte[] bytes, String filePath) {
        for (byte b : bytes) {
            if (b == 0) {
                throw new ToolException(
                        ErrorCode.FILE_BINARY, "File is not valid UTF-8 text", Map.of("path", filePath));
            }
        }
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new ToolException(ErrorCode.FILE_BINARY, "File is not valid UTF-8 text", Map.of("path", filePath));
        }
    }
}

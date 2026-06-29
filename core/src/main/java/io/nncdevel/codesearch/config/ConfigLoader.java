package io.nncdevel.codesearch.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nncdevel.codesearch.util.Hosting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses and validates the JSON configuration files and aborts startup on any violation (spec
 * §7.4). Path resolution honors CLI &gt; environment variable &gt; file &gt; built-in default.
 *
 * <p>All methods are static; the class is not instantiable.
 */
public final class ConfigLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final long MIN_REFRESH_SECONDS = 60;
    private static final long DEFAULT_REFRESH_SECONDS = 900;
    private static final String DEFAULT_WORKSPACE_DIR = "workspaces";

    private static final Set<PosixFilePermission> ALLOWED_SECRET_PERMS =
            Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private ConfigLoader() {}

    // ---- repos.json ----------------------------------------------------------

    /**
     * Loads and validates {@code repos.json}.
     *
     * @param path the path to the repository catalog file
     * @return the parsed repository catalog
     * @throws ConfigException if the file is missing, not valid JSON, or violates a startup
     *     invariant (missing field, duplicate id, invalid hosting, refresh below the minimum)
     */
    public static RepositoriesFile loadRepositories(Path path) {
        JsonNode root = parse(path);
        String workspaceRoot = optionalText(root, "workspace_root");
        String secretsPath = optionalText(root, "secrets");

        JsonNode repos = root.path("repositories");
        if (!repos.isArray() || repos.isEmpty()) {
            throw new ConfigException("repos.json must declare a non-empty \"repositories\" array");
        }

        List<RepositoryConfig> result = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (int i = 0; i < repos.size(); i++) {
            JsonNode node = repos.get(i);
            if (!node.isObject()) {
                throw new ConfigException("repositories[" + i + "] must be an object");
            }
            RepositoryConfig repo = readRepository(node, i);
            if (!seenIds.add(repo.id())) {
                throw new ConfigException("duplicate repository id: " + repo.id());
            }
            result.add(repo);
        }
        return new RepositoriesFile(workspaceRoot, secretsPath, result);
    }

    private static RepositoryConfig readRepository(JsonNode node, int index) {
        String id = requireText(node, "id", "repositories[" + index + "]");
        if (!ID_PATTERN.matcher(id).matches() || id.length() > 64) {
            throw new ConfigException("invalid repository id: " + id);
        }
        String remote = requireText(node, "remote", id);
        String branch = requireText(node, "branch", id);
        String hostingRaw = requireText(node, "hosting", id);
        Hosting hosting;
        try {
            hosting = Hosting.fromWire(hostingRaw);
        } catch (IllegalArgumentException e) {
            throw new ConfigException("invalid hosting for " + id + ": " + hostingRaw);
        }
        String hostingBaseUrl = requireText(node, "hosting_base_url", id);

        long refresh = DEFAULT_REFRESH_SECONDS;
        if (node.has("refresh_interval_seconds")) {
            JsonNode r = node.get("refresh_interval_seconds");
            if (!r.isIntegralNumber() || r.asLong() < MIN_REFRESH_SECONDS) {
                throw new ConfigException(
                        "refresh_interval_seconds for " + id + " must be an integer >= " + MIN_REFRESH_SECONDS);
            }
            refresh = r.asLong();
        }

        List<String> excludePaths = new ArrayList<>();
        JsonNode excludes = node.path("exclude_paths");
        if (excludes.isArray()) {
            for (JsonNode e : excludes) {
                excludePaths.add(e.asText());
            }
        }

        String description = optionalText(node, "description");
        if (description != null && description.length() > 8192) {
            throw new ConfigException("description for " + id + " exceeds 8192 characters");
        }

        return new RepositoryConfig(id, remote, branch, hosting, hostingBaseUrl, refresh, excludePaths, description);
    }

    // ---- secrets.json --------------------------------------------------------

    /**
     * Loads secrets. Entries whose key does not match a known repository id are logged as
     * {@code config_warning} and ignored (spec §7.2).
     *
     * @param path the secrets file path, or {@code null} for anonymous access only
     * @param knownRepositoryIds the configured repository ids, used to detect orphan entries
     * @return the parsed secrets, or {@link SecretsFile#empty()} when {@code path} is {@code null}
     * @throws ConfigException if the file permissions are looser than 600, the file is not valid
     *     JSON, or an entry has an invalid {@code auth_type} or is missing a required credential
     */
    public static SecretsFile loadSecrets(Path path, Set<String> knownRepositoryIds) {
        if (path == null) {
            return SecretsFile.empty();
        }
        checkSecretPermissions(path);
        JsonNode root = parse(path);

        Map<String, SecretEntry> entries = new LinkedHashMap<>();
        JsonNode secrets = root.path("secrets");
        if (secrets.isObject()) {
            var fields = secrets.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String id = field.getKey();
                if (!knownRepositoryIds.contains(id)) {
                    LOG.warn("config_warning: orphan secret entry ignored: {}", id);
                    continue;
                }
                entries.put(id, readSecret(id, field.getValue()));
            }
        }
        return new SecretsFile(entries);
    }

    private static SecretEntry readSecret(String id, JsonNode node) {
        String authRaw = optionalText(node, "auth_type");
        if (authRaw == null) {
            throw new ConfigException("secrets." + id + " is missing auth_type");
        }
        AuthType authType;
        try {
            authType = AuthType.fromWire(authRaw);
        } catch (IllegalArgumentException e) {
            throw new ConfigException("invalid auth_type for secrets." + id + ": " + authRaw);
        }
        String token = optionalText(node, "token");
        String sshKeyPath = optionalText(node, "ssh_key_path");
        switch (authType) {
            case TOKEN -> {
                if (token == null || token.isEmpty()) {
                    throw new ConfigException("secrets." + id + " with auth_type=token requires token");
                }
            }
            case SSH_KEY -> {
                if (sshKeyPath == null || sshKeyPath.isEmpty()) {
                    throw new ConfigException("secrets." + id + " with auth_type=ssh_key requires ssh_key_path");
                }
            }
            case NONE -> {
                // no extra fields required
            }
        }
        return new SecretEntry(authType, token, sshKeyPath);
    }

    private static void checkSecretPermissions(Path path) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
            if (!ALLOWED_SECRET_PERMS.containsAll(perms)) {
                throw new ConfigException(
                        "secrets file permissions must be 600 or stricter: " + path + " has " + perms);
            }
        } catch (UnsupportedOperationException e) {
            // Non-POSIX filesystem: skip the permission check.
        } catch (IOException e) {
            throw new ConfigException("cannot read secrets file: " + path, e);
        }
    }

    // ---- server.json ---------------------------------------------------------

    /**
     * Parses {@code server.json} into a {@link ServerConfig}; missing fields fall back to defaults.
     *
     * @param path the server config path, or {@code null} to use built-in defaults
     * @return the resolved server configuration
     * @throws ConfigException if the file is not valid JSON, the transport is unknown, or the port is
     *     out of range
     */
    public static ServerConfig loadServerConfig(Path path) {
        if (path == null) {
            return ServerConfig.defaults();
        }
        JsonNode root = parse(path);
        Transport transport = ServerConfig.DEFAULT_TRANSPORT;
        if (root.has("transport")) {
            try {
                transport = Transport.fromWire(root.get("transport").asText());
            } catch (IllegalArgumentException e) {
                throw new ConfigException(
                        "invalid transport: " + root.get("transport").asText());
            }
        }
        String host = root.has("host") ? root.get("host").asText() : ServerConfig.DEFAULT_HOST;
        int port = ServerConfig.DEFAULT_PORT;
        if (root.has("port")) {
            JsonNode p = root.get("port");
            if (!p.isIntegralNumber() || p.asInt() < 1 || p.asInt() > 65535) {
                throw new ConfigException("port must be between 1 and 65535");
            }
            port = p.asInt();
        }
        boolean enableScheduler = root.has("enable_scheduler")
                ? root.get("enable_scheduler").asBoolean(false)
                : ServerConfig.DEFAULT_ENABLE_SCHEDULER;
        return new ServerConfig(transport, host, port, enableScheduler);
    }

    // ---- path resolution -----------------------------------------------------

    /**
     * Resolves the repos.json path: CLI &gt; env &gt; {@code ./config/repos.json}.
     *
     * @param cli the value of the {@code --repos} flag, or {@code null}
     * @param env the value of {@code CODE_SEARCH_REPOS_PATH}, or {@code null}
     * @param workingDir the directory used for auto-discovery
     * @return the resolved path, or {@code null} if no explicit value is set and no discovery file
     *     exists
     */
    public static Path resolveReposPath(String cli, String env, Path workingDir) {
        Path explicit = firstNonBlank(cli, env);
        if (explicit != null) {
            return explicit;
        }
        Path discovered = workingDir.resolve("config/repos.json");
        return Files.exists(discovered) ? discovered : null;
    }

    /**
     * Resolves the secrets.json path: CLI &gt; env &gt; repos.json {@code secrets} &gt;
     * {@code ./config/secrets.json}.
     *
     * @param cli the value of the {@code --secrets} flag, or {@code null}
     * @param env the value of {@code CODE_SEARCH_SECRETS_PATH}, or {@code null}
     * @param fromReposFile the {@code secrets} field from repos.json, or {@code null}
     * @param workingDir the directory used for auto-discovery
     * @return the resolved path, or {@code null} if no explicit value is set and no discovery file
     *     exists
     */
    public static Path resolveSecretsPath(String cli, String env, String fromReposFile, Path workingDir) {
        Path explicit = firstNonBlank(cli, env, fromReposFile);
        if (explicit != null) {
            return explicit;
        }
        Path discovered = workingDir.resolve("config/secrets.json");
        return Files.exists(discovered) ? discovered : null;
    }

    /**
     * Resolves the workspace root: CLI &gt; env &gt; repos.json &gt; {@code ./workspaces}.
     *
     * @param cli the value of the {@code --workspace-root} flag, or {@code null}
     * @param env the value of {@code CODE_SEARCH_WORKSPACE_ROOT}, or {@code null}
     * @param fromReposFile the {@code workspace_root} field from repos.json, or {@code null}
     * @param workingDir the directory used to resolve the built-in default
     * @return the resolved workspace root path
     */
    public static Path resolveWorkspaceRoot(String cli, String env, String fromReposFile, Path workingDir) {
        Path explicit = firstNonBlank(cli, env, fromReposFile);
        return explicit != null ? explicit : workingDir.resolve(DEFAULT_WORKSPACE_DIR);
    }

    /**
     * Resolves the server.json path: CLI &gt; env &gt; {@code ./config/server.json}.
     *
     * @param cli the value of the {@code --config} flag, or {@code null}
     * @param env the value of {@code CODE_SEARCH_CONFIG_PATH}, or {@code null}
     * @param workingDir the directory used for auto-discovery
     * @return the resolved path, or {@code null} if no explicit value is set and no discovery file
     *     exists
     */
    public static Path discoverServerConfigPath(String cli, String env, Path workingDir) {
        Path explicit = firstNonBlank(cli, env);
        if (explicit != null) {
            return explicit;
        }
        Path discovered = workingDir.resolve("config/server.json");
        return Files.exists(discovered) ? discovered : null;
    }

    // ---- helpers -------------------------------------------------------------

    private static Path firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return Path.of(c);
            }
        }
        return null;
    }

    private static JsonNode parse(Path path) {
        if (!Files.exists(path)) {
            throw new ConfigException("config file not found: " + path);
        }
        try {
            return MAPPER.readTree(path.toFile());
        } catch (IOException e) {
            throw new ConfigException("invalid JSON in " + path + ": " + e.getMessage(), e);
        }
    }

    private static String requireText(JsonNode node, String field, String context) {
        String value = optionalText(node, field);
        if (value == null || value.isEmpty()) {
            throw new ConfigException("missing required field '" + field + "' in " + context);
        }
        return value;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() ? v.asText() : null;
    }
}

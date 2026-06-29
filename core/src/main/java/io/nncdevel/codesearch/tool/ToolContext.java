package io.nncdevel.codesearch.tool;

import io.nncdevel.codesearch.backend.GitLsBackend;
import io.nncdevel.codesearch.backend.RipgrepBackend;
import io.nncdevel.codesearch.repo.RepositoryManager;

/**
 * Shared collaborators handed to each tool: the repository manager and the search backends (design
 * §3). Tools resolve a repository to a workspace through the manager, then invoke a backend.
 *
 * @param repositories the repository manager used to resolve and gate access
 * @param ripgrep the ripgrep backend for content search and file listing
 * @param gitLs the {@code git ls-files} backend for tree listing
 */
public record ToolContext(RepositoryManager repositories, RipgrepBackend ripgrep, GitLsBackend gitLs) {}

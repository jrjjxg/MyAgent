package com.xg.platform.tools;

import com.xg.platform.contracts.skill.SkillDescriptor;
import com.xg.platform.contracts.skill.UpsertSkillRequest;
import com.xg.platform.contracts.validation.PlatformIds;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

public class SkillRegistry {

    private final List<SkillSourceRoot> sourceRoots;
    private final McpServerRegistry mcpServerRegistry;
    private final SkillConfigStore skillConfigStore;
    private volatile CachedCatalog cachedCatalog;

    public SkillRegistry(Path publicSkillsRoot, McpServerRegistry mcpServerRegistry) {
        this(publicSkillsRoot, null, List.of(), mcpServerRegistry, SkillConfigStore.disabled());
    }

    public SkillRegistry(Path publicSkillsRoot,
                         Path customSkillsRoot,
                         List<Path> extraRoots,
                         McpServerRegistry mcpServerRegistry) {
        this(publicSkillsRoot, customSkillsRoot, extraRoots, mcpServerRegistry, SkillConfigStore.disabled());
    }

    public SkillRegistry(Path publicSkillsRoot,
                         Path customSkillsRoot,
                         List<Path> extraRoots,
                         McpServerRegistry mcpServerRegistry,
                         SkillConfigStore skillConfigStore) {
        this.mcpServerRegistry = mcpServerRegistry;
        this.skillConfigStore = skillConfigStore == null ? SkillConfigStore.disabled() : skillConfigStore;
        this.sourceRoots = buildSourceRoots(publicSkillsRoot, customSkillsRoot, extraRoots);
    }

    public SkillRuntimeSnapshot snapshotForUser(String userId) {
        List<SkillDefinition> skills = loadCatalog().skills().stream()
                .map(skill -> applyAvailability(userId, skill))
                .toList();
        return new SkillRuntimeSnapshot(userId, skills);
    }

    public List<SkillDefinition> listSkills(String userId) {
        return snapshotForUser(userId).skills();
    }

    public List<SkillDefinition> listDiscoveredSkills() {
        return loadCatalog().skills();
    }

    public List<SkillDefinition> listEnabledSkills(String userId) {
        return snapshotForUser(userId).skills().stream()
                .filter(SkillDefinition::enabled)
                .toList();
    }

    public List<SkillDescriptor> listDescriptors(String userId) {
        return snapshotForUser(userId).descriptors();
    }

    public SkillDefinition requireSkill(String userId, String skillId) {
        return applyAvailability(userId, requireDiscoveredSkill(skillId));
    }

    public SkillDefinition loadSkillContent(String userId, String skillId) {
        SkillDefinition skill = requireSkill(userId, skillId);
        return skill.withBody(readSkillBody(skill.sourcePath()));
    }

    public SkillResourceContent loadSkillResource(String userId, String skillId, String resourcePath, Integer maxChars) {
        SkillDefinition skill = requireSkill(userId, skillId);
        String normalizedPath = normalizeDeclaredResourcePath(resourcePath);
        if (!skill.resources().contains(normalizedPath)) {
            throw new IllegalArgumentException("Resource not declared for skill %s: %s".formatted(skill.skillId(), normalizedPath));
        }
        Path skillRoot = skill.sourcePath().getParent();
        Path resolved = skillRoot.resolve(normalizedPath).normalize();
        ensureInsideSkillRoot(skillRoot, resolved);
        try {
            String text = Files.readString(resolved, StandardCharsets.UTF_8);
            boolean truncated = false;
            int charLimit = maxChars == null ? 0 : Math.max(0, maxChars);
            if (charLimit > 0 && text.length() > charLimit) {
                text = text.substring(0, charLimit);
                truncated = true;
            }
            return new SkillResourceContent(skill.skillId(), normalizedPath, resolved, text, truncated);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read skill resource: " + normalizedPath, exception);
        }
    }

    private SkillDefinition requireDiscoveredSkill(String skillId) {
        String normalizedSkillId = skillId == null ? "" : skillId.trim();
        if (normalizedSkillId.isBlank()) {
            throw new IllegalArgumentException("skillId must not be blank");
        }
        return loadCatalog().skills().stream()
                .filter(skill -> skill.skillId().equals(normalizedSkillId) || skill.sourceKey().equalsIgnoreCase(normalizedSkillId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + normalizedSkillId));
    }

    public SkillDefinition requireEnabledSkill(String userId, String skillId) {
        return requireEnabledSkill(userId, skillId, null);
    }

    public SkillDefinition requireEnabledSkill(String userId, String skillId, SkillRuntimeSnapshot snapshot) {
        SkillRuntimeSnapshot resolvedSnapshot = snapshot == null ? snapshotForUser(userId) : snapshot;
        try {
            return resolvedSnapshot.requireEnabledSkill(skillId);
        } catch (NoSuchElementException exception) {
            throw new IllegalArgumentException("Unknown or disabled skill: " + skillId, exception);
        }
    }

    public SkillDefinition upsertCustomSkill(String userId, String skillId, UpsertSkillRequest request) {
        PlatformIds.requireIdentifier(
                skillId != null && !skillId.isBlank() ? skillId : request == null ? null : request.skillId(),
                "skillId"
        );
        throw new UnsupportedOperationException("User-managed skills are disabled");
    }

    public void deleteCustomSkill(String userId, String skillId) {
        PlatformIds.requireIdentifier(skillId, "skillId");
        throw new UnsupportedOperationException("User-managed skills are disabled");
    }

    public void setSkillEnabled(String userId, String skillId, boolean enabled) {
        requireSkill(userId, skillId);
        throw new UnsupportedOperationException("User-managed skill flags are disabled");
    }

    private List<SkillSourceRoot> buildSourceRoots(Path publicSkillsRoot,
                                                   Path customSkillsRoot,
                                                   List<Path> extraRoots) {
        List<SkillSourceRoot> roots = new ArrayList<>();
        if (extraRoots != null) {
            for (int index = extraRoots.size() - 1; index >= 0; index--) {
                Path path = extraRoots.get(index);
                if (path != null) {
                    roots.add(new SkillSourceRoot("extra-" + (index + 1), path));
                }
            }
        }
        if (customSkillsRoot != null) {
            roots.add(new SkillSourceRoot("custom", customSkillsRoot));
        }
        if (publicSkillsRoot != null) {
            roots.add(new SkillSourceRoot("public", publicSkillsRoot));
        }
        return List.copyOf(roots);
    }

    private CachedCatalog loadCatalog() {
        List<RootSignature> currentSignatures = computeSignatures();
        CachedCatalog current = cachedCatalog;
        if (current != null && current.signatures().equals(currentSignatures)) {
            return current;
        }
        synchronized (this) {
            current = cachedCatalog;
            currentSignatures = computeSignatures();
            if (current != null && current.signatures().equals(currentSignatures)) {
                return current;
            }
            CachedCatalog reloaded = new CachedCatalog(currentSignatures, scanSkills());
            cachedCatalog = reloaded;
            return reloaded;
        }
    }

    private List<RootSignature> computeSignatures() {
        List<RootSignature> signatures = new ArrayList<>();
        for (SkillSourceRoot sourceRoot : sourceRoots) {
            signatures.add(signatureFor(sourceRoot));
        }
        return List.copyOf(signatures);
    }

    private RootSignature signatureFor(SkillSourceRoot sourceRoot) {
        Path root = sourceRoot.path();
        if (root == null || !Files.exists(root)) {
            return new RootSignature(sourceRoot.source(), root == null ? "<missing>" : root.toAbsolutePath().normalize().toString(), 0L, 0L);
        }
        final long[] latestModified = {0L};
        final long[] count = {0L};
        try {
            Files.walk(root)
                    .filter(path -> path.getFileName().toString().equals("SKILL.md"))
                    .forEach(path -> {
                        count[0]++;
                        try {
                            FileTime lastModifiedTime = Files.getLastModifiedTime(path);
                            latestModified[0] = Math.max(latestModified[0], lastModifiedTime.toMillis());
                        } catch (IOException exception) {
                            throw new UncheckedIOException(exception);
                        }
                    });
            return new RootSignature(sourceRoot.source(), root.toAbsolutePath().normalize().toString(), latestModified[0], count[0]);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read skill root signature", exception);
        }
    }

    private List<SkillDefinition> scanSkills() {
        List<SkillDefinition> skills = new ArrayList<>();
        for (SkillSourceRoot sourceRoot : sourceRoots) {
            skills.addAll(scanSkills(sourceRoot));
        }
        return List.copyOf(skills);
    }

    private List<SkillDefinition> scanSkills(SkillSourceRoot sourceRoot) {
        List<SkillDefinition> skills = new ArrayList<>();
        Path root = sourceRoot.path();
        if (root == null || !Files.exists(root)) {
            return skills;
        }
        try {
            Files.walk(root)
                    .filter(path -> path.getFileName().toString().equals("SKILL.md"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> skills.add(parseSkill(path, sourceRoot.source())));
            return skills;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to scan skills", exception);
        }
    }

    private SkillDefinition parseSkill(Path path, String source) {
        try {
            String frontMatter = readFrontMatter(path);
            SkillMetadata metadata = parseFrontMatter(frontMatter);
            String skillId = PlatformIds.requireIdentifier(metadata.required("name"), "skillId");
            return new SkillDefinition(
                    skillId,
                    source + ":" + skillId,
                    metadata.required("description"),
                    metadata.optional("summary"),
                    metadata.optional("homepage"),
                    metadata.optional("primaryEnv", "primary-env"),
                    metadata.list("requiredEnvs", "required-envs"),
                    metadata.list("triggers"),
                    metadata.list("preferredTools", "preferred-tools"),
                    metadata.list("allowedTools", "allowed-tools"),
                    metadata.list("resources"),
                    metadata.list("mcpServers", "mcp-servers"),
                    discoverPackageCommands(path.getParent()),
                    metadata.bool("requiresDocuments", "requires-documents"),
                    metadata.bool("requiresWeb", "requires-web"),
                    metadata.optional("agent"),
                    SkillInvocation.fromFrontMatter(metadata.optional("invocation")),
                    SkillExecutionMode.fromFrontMatter(metadata.optional("execution")),
                    path.toAbsolutePath().normalize(),
                    "",
                    true,
                    source,
                    SkillAvailabilityStatus.READY,
                    ""
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to parse skill: " + path, exception);
        }
    }

    private List<SkillPackageCommand> discoverPackageCommands(Path skillRoot) {
        if (skillRoot == null) {
            return List.of();
        }
        Path scriptsRoot = skillRoot.resolve("scripts");
        if (!Files.isDirectory(scriptsRoot)) {
            return List.of();
        }
        List<SkillPackageCommand> commands = new ArrayList<>();
        Map<String, Integer> seen = new LinkedHashMap<>();
        try {
            Files.walk(scriptsRoot)
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> {
                        SkillCommandRunner runner = SkillCommandRunner.infer(path);
                        if (runner == null) {
                            return;
                        }
                        String commandId = uniqueCommandId(seen, commandIdFor(path, scriptsRoot));
                        String relativePath = skillRoot.relativize(path).toString().replace('\\', '/');
                        boolean backgroundSuggested = path.getFileName().toString().toLowerCase(Locale.ROOT).contains("daemon");
                        commands.add(new SkillPackageCommand(commandId, relativePath, runner, backgroundSuggested));
                    });
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to inspect skill package scripts", exception);
        }
        return List.copyOf(commands);
    }

    private String commandIdFor(Path path, Path scriptsRoot) {
        String relative = scriptsRoot.relativize(path).toString().replace('\\', '/');
        int extensionIndex = relative.lastIndexOf('.');
        String withoutExtension = extensionIndex > 0 ? relative.substring(0, extensionIndex) : relative;
        String normalized = withoutExtension
                .replace('/', '_')
                .replaceAll("[^A-Za-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "")
                .toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "command" : normalized;
    }

    private String uniqueCommandId(Map<String, Integer> seen, String base) {
        int count = seen.getOrDefault(base, 0);
        seen.put(base, count + 1);
        return count == 0 ? base : base + "_" + (count + 1);
    }

    private SkillDefinition applyAvailability(String userId, SkillDefinition skill) {
        SkillAvailability availability = evaluateAvailability(userId, skill);
        return skill.withAvailability(
                availability.status(),
                availability.reason(),
                availability.status() == SkillAvailabilityStatus.READY
        );
    }

    private SkillAvailability evaluateAvailability(String userId, SkillDefinition skill) {
        if (!mcpServerRegistry.isSkillEnabled(userId, skill.skillId())) {
            return new SkillAvailability(SkillAvailabilityStatus.DISABLED, "disabled by config");
        }
        if (!skillConfigStore.find(userId, skill.skillId()).map(SkillUserConfig::enabled).orElse(true)) {
            return new SkillAvailability(SkillAvailabilityStatus.DISABLED, "disabled by user config");
        }
        if (requiresSearchTools(skill.skillId()) && !mcpServerRegistry.hasSearchTools(userId)) {
            return new SkillAvailability(SkillAvailabilityStatus.UNAVAILABLE, "search tools unavailable");
        }
        List<String> missingEnvs = missingRequiredEnvs(userId, skill);
        if (!missingEnvs.isEmpty()) {
            return new SkillAvailability(
                    SkillAvailabilityStatus.MISSING_ENV,
                    "missing env: " + String.join(", ", missingEnvs)
            );
        }
        return new SkillAvailability(SkillAvailabilityStatus.READY, "");
    }

    private boolean requiresSearchTools(String skillId) {
        return "docs.web-research".equals(skillId)
                || "research.deep-research".equals(skillId)
                || "research.github-repo".equals(skillId)
                || "research.competitive-analysis".equals(skillId);
    }

    private List<String> missingRequiredEnvs(String userId, SkillDefinition skill) {
        Map<String, String> configured = skillConfigStore.find(userId, skill.skillId())
                .map(SkillUserConfig::env)
                .orElse(Map.of());
        Set<String> required = new LinkedHashSet<>();
        if (skill.primaryEnv() != null && !skill.primaryEnv().isBlank()) {
            required.add(skill.primaryEnv().trim());
        }
        required.addAll(skill.requiredEnvs());
        List<String> missing = new ArrayList<>();
        for (String key : required) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String configuredValue = configured.get(key);
            if (configuredValue != null && !configuredValue.isBlank()) {
                continue;
            }
            String processValue = System.getenv(key);
            if (processValue != null && !processValue.isBlank()) {
                continue;
            }
            missing.add(key);
        }
        return List.copyOf(missing);
    }

    private SkillMetadata parseFrontMatter(String frontMatter) {
        Map<String, String> scalars = new LinkedHashMap<>();
        Map<String, List<String>> lists = new LinkedHashMap<>();
        String currentListKey = null;
        for (String rawLine : frontMatter.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("- ")) {
                if (currentListKey != null) {
                    lists.computeIfAbsent(currentListKey, ignored -> new ArrayList<>())
                            .add(unquote(line.substring(2).trim()));
                }
                continue;
            }
            int separatorIndex = line.indexOf(':');
            if (separatorIndex <= 0) {
                continue;
            }
            String key = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + 1).trim();
            if (value.isBlank()) {
                currentListKey = key;
                lists.putIfAbsent(key, new ArrayList<>());
                continue;
            }
            currentListKey = null;
            scalars.put(key, unquote(value));
        }
        return new SkillMetadata(scalars, lists);
    }

    private static String unquote(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            return normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private String readFrontMatter(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String firstLine = reader.readLine();
            if (firstLine == null || !"---".equals(firstLine.trim())) {
                throw new IllegalArgumentException("Skill file missing front matter: " + path);
            }
            StringBuilder builder = new StringBuilder();
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if ("---".equals(line.trim())) {
                    return builder.toString();
                }
                if (!first) {
                    builder.append(System.lineSeparator());
                }
                builder.append(line);
                first = false;
            }
        }
        throw new IllegalArgumentException("Skill file missing front matter terminator: " + path);
    }

    private String readSkillBody(Path path) {
        try {
            SkillFileSections sections = splitSkillFile(Files.readString(path, StandardCharsets.UTF_8));
            return sections.body();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load skill body: " + path, exception);
        }
    }

    private SkillFileSections splitSkillFile(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.startsWith("---")) {
            throw new IllegalArgumentException("Skill file missing front matter");
        }
        int endIndex = normalized.indexOf("\n---", 3);
        if (endIndex < 0) {
            throw new IllegalArgumentException("Skill file missing front matter terminator");
        }
        int bodyStart = endIndex + 4;
        if (bodyStart < normalized.length() && normalized.charAt(bodyStart) == '\n') {
            bodyStart++;
        }
        return new SkillFileSections(
                normalized.substring(4, endIndex),
                normalized.substring(Math.min(bodyStart, normalized.length())).trim()
        );
    }

    private String normalizeDeclaredResourcePath(String resourcePath) {
        String normalized = resourcePath == null ? "" : resourcePath.trim().replace('\\', '/');
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("resourcePath must not be blank");
        }
        return normalized;
    }

    private void ensureInsideSkillRoot(Path skillRoot, Path candidate) {
        Path normalizedRoot = skillRoot.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Skill resource path escapes skill root");
        }
    }

    private record SkillSourceRoot(String source, Path path) {

        private SkillSourceRoot {
            path = path == null ? null : path.toAbsolutePath().normalize();
        }
    }

    private record RootSignature(String source, String rootPath, long latestModifiedMillis, long fileCount) {
    }

    private record CachedCatalog(List<RootSignature> signatures, List<SkillDefinition> skills) {
    }

    private record SkillAvailability(SkillAvailabilityStatus status, String reason) {
    }

    private record SkillFileSections(String frontMatter, String body) {
    }

    private record SkillMetadata(
            Map<String, String> scalars,
            Map<String, List<String>> lists
    ) {

        private String required(String key) {
            String value = optional(key);
            if (value == null || value.isBlank()) {
                throw new NoSuchElementException("Missing skill front matter key: " + key);
            }
            return value;
        }

        private String optional(String... keys) {
            for (String key : keys) {
                String value = scalars.get(key);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }

        private List<String> list(String... keys) {
            for (String key : keys) {
                List<String> value = lists.get(key);
                if (value != null) {
                    return List.copyOf(value);
                }
                String scalar = scalars.get(key);
                if (scalar != null) {
                    return parseScalarList(scalar);
                }
            }
            return List.of();
        }

        private boolean bool(String... keys) {
            String value = optional(keys);
            return value != null && Boolean.parseBoolean(value);
        }

        private List<String> parseScalarList(String value) {
            if (value == null || value.isBlank()) {
                return List.of();
            }
            String normalized = value.trim();
            if (normalized.startsWith("[") && normalized.endsWith("]")) {
                normalized = normalized.substring(1, normalized.length() - 1);
            }
            List<String> items = new ArrayList<>();
            for (String token : normalized.split(",")) {
                String item = unquote(token);
                if (item != null && !item.isBlank()) {
                    items.add(item);
                }
            }
            return List.copyOf(items);
        }
    }
}

package com.xg.platform.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ConfigurationProperties(prefix = "platform")
public class PlatformProperties {

    private Path dataRoot = Path.of("data");
    private Path skillsRoot = Path.of("skills");
    private Path extensionsConfigPath = Path.of("extensions.json");
    private String devUserId = "dev-user";
    private final Ai ai = new Ai();
    private final Model model = new Model();
    private final Tools tools = new Tools();
    private final Debug debug = new Debug();
    private final Chat chat = new Chat();
    private final Async async = new Async();
    private final Ingest ingest = new Ingest();
    private final Memory memory = new Memory();
    private final Upload upload = new Upload();
    private final Skills skills = new Skills();
    private final Research research = new Research();

    public Path getDataRoot() {
        return dataRoot;
    }

    public void setDataRoot(Path dataRoot) {
        this.dataRoot = dataRoot;
    }

    public Path getResolvedDataRoot() {
        return resolvePath(dataRoot);
    }

    public Path getSkillsRoot() {
        return skillsRoot;
    }

    public void setSkillsRoot(Path skillsRoot) {
        this.skillsRoot = skillsRoot;
    }

    public Path getResolvedSkillsRoot() {
        return resolvePath(skillsRoot);
    }

    public Path getExtensionsConfigPath() {
        return extensionsConfigPath;
    }

    public void setExtensionsConfigPath(Path extensionsConfigPath) {
        this.extensionsConfigPath = extensionsConfigPath;
    }

    public Path getResolvedExtensionsConfigPath() {
        return resolvePath(extensionsConfigPath);
    }

    public Path resolvePath(Path path) {
        if (path == null) {
            return null;
        }
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return resolveRelativePath(path);
    }

    private Path projectRoot() {
        String multiModuleRoot = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleRoot != null && !multiModuleRoot.isBlank()) {
            return Path.of(multiModuleRoot).toAbsolutePath().normalize();
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    private Path resolveRelativePath(Path path) {
        Set<Path> searchRoots = new LinkedHashSet<>();
        searchRoots.add(projectRoot());
        searchRoots.add(Path.of("").toAbsolutePath().normalize());

        for (Path root : searchRoots) {
            Path located = findExistingPath(root, path);
            if (located != null) {
                return located;
            }
        }

        return projectRoot().resolve(path).normalize();
    }

    private Path findExistingPath(Path start, Path relativePath) {
        if (start == null) {
            return null;
        }
        Path current = start.toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(relativePath).normalize();
            if (java.nio.file.Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    public String getDevUserId() {
        return devUserId;
    }

    public void setDevUserId(String devUserId) {
        this.devUserId = devUserId;
    }

    public Ai getAi() {
        return ai;
    }

    public Tools getTools() {
        return tools;
    }

    public Debug getDebug() {
        return debug;
    }

    public Chat getChat() {
        return chat;
    }

    public Model getModel() {
        return model;
    }

    public Async getAsync() {
        return async;
    }

    public Memory getMemory() {
        return memory;
    }

    public Ingest getIngest() {
        return ingest;
    }

    public Upload getUpload() {
        return upload;
    }

    public Skills getSkills() {
        return skills;
    }

    public Research getResearch() {
        return research;
    }

    public static class Ai {

        private final Gemini gemini = new Gemini();
        private final Execution execution = new Execution();

        public Gemini getGemini() {
            return gemini;
        }

        public Execution getExecution() {
            return execution;
        }
    }

    public static class Gemini {

        private String baseUrl = "https://generativelanguage.googleapis.com";
        private String apiKey = System.getenv("GOOGLE_API_KEY");
        private String model;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Execution {

        private String impl = "legacy";

        public String getImpl() {
            return impl;
        }

        public void setImpl(String impl) {
            this.impl = impl;
        }
    }

    public static class Model {

        private String defaultProvider = "gemini";
        private final Providers providers = new Providers();
        private final Router router = new Router();

        public String getDefaultProvider() {
            return defaultProvider;
        }

        public void setDefaultProvider(String defaultProvider) {
            this.defaultProvider = defaultProvider;
        }

        public Providers getProviders() {
            return providers;
        }

        public Router getRouter() {
            return router;
        }
    }

    public static class Router {

        private boolean enabled = false;
        private String provider;
        private String model;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Providers {

        private final Provider gemini = new Provider();
        private final Provider openai = new Provider();
        private final Provider deepseek = new Provider();

        public Provider getGemini() {
            return gemini;
        }

        public Provider getOpenai() {
            return openai;
        }

        public Provider getDeepseek() {
            return deepseek;
        }
    }

    public static class Provider {

        private boolean enabled = true;
        private String baseUrl;
        private String apiKey;
        private String model;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Tools {

        private String pythonCommand = "python";
        private Path documentScript = Path.of("tools", "document_tools.py");
        private long timeoutSeconds = 30;
        private final Web web = new Web();

        public String getPythonCommand() {
            return pythonCommand;
        }

        public void setPythonCommand(String pythonCommand) {
            this.pythonCommand = pythonCommand;
        }

        public Path getDocumentScript() {
            return documentScript;
        }

        public void setDocumentScript(Path documentScript) {
            this.documentScript = documentScript;
        }

        public long getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public Web getWeb() {
            return web;
        }
    }

    public static class Web {

        private static final String DEFAULT_BROWSER_USER_AGENT =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";

        private String provider = "duckduckgo";
        private String searchApiBaseUrl = "https://api.duckduckgo.com/";
        private String tavilyApiKey = System.getenv("TAVILY_API_KEY");
        private String userAgent = DEFAULT_BROWSER_USER_AGENT;
        private long timeoutSeconds = 20;
        private int maxResults = 5;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getSearchApiBaseUrl() {
            return searchApiBaseUrl;
        }

        public void setSearchApiBaseUrl(String searchApiBaseUrl) {
            this.searchApiBaseUrl = searchApiBaseUrl;
        }

        public String getTavilyApiKey() {
            return tavilyApiKey;
        }

        public void setTavilyApiKey(String tavilyApiKey) {
            this.tavilyApiKey = tavilyApiKey;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }

        public long getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }
    }

    public static class Upload {

        private boolean redisStateEnabled = true;
        private int maxChunkSizeBytes = 8 * 1024 * 1024;
        private long sessionTtlHours = 24;

        public boolean isRedisStateEnabled() {
            return redisStateEnabled;
        }

        public void setRedisStateEnabled(boolean redisStateEnabled) {
            this.redisStateEnabled = redisStateEnabled;
        }

        public int getMaxChunkSizeBytes() {
            return maxChunkSizeBytes;
        }

        public void setMaxChunkSizeBytes(int maxChunkSizeBytes) {
            this.maxChunkSizeBytes = maxChunkSizeBytes;
        }

        public long getSessionTtlHours() {
            return sessionTtlHours;
        }

        public void setSessionTtlHours(long sessionTtlHours) {
            this.sessionTtlHours = sessionTtlHours;
        }
    }

    public static class Ingest {

        private int maxAttempts = 3;
        private long staleRunningMinutes = 15;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getStaleRunningMinutes() {
            return staleRunningMinutes;
        }

        public void setStaleRunningMinutes(long staleRunningMinutes) {
            this.staleRunningMinutes = staleRunningMinutes;
        }
    }

    public static class Skills {

        private Path customRoot;
        private List<Path> extraRoots = new ArrayList<>();
        private String secretEncryptionKey;

        public Path getCustomRoot() {
            return customRoot;
        }

        public void setCustomRoot(Path customRoot) {
            this.customRoot = customRoot;
        }

        public List<Path> getExtraRoots() {
            return extraRoots;
        }

        public void setExtraRoots(List<Path> extraRoots) {
            this.extraRoots = extraRoots == null ? new ArrayList<>() : new ArrayList<>(extraRoots);
        }

        public String getSecretEncryptionKey() {
            return secretEncryptionKey;
        }

        public void setSecretEncryptionKey(String secretEncryptionKey) {
            this.secretEncryptionKey = secretEncryptionKey;
        }
    }

    public static class Research {

        private final UnitAgent unitAgent = new UnitAgent();
        private final ResearchExecution execution = new ResearchExecution();

        public UnitAgent getUnitAgent() {
            return unitAgent;
        }

        public ResearchExecution getExecution() {
            return execution;
        }
    }

    public static class ResearchExecution {

        private int maxIterations = 8;
        private long maxWallTimeMs = 600_000L;

        public int getMaxIterations() {
            return maxIterations;
        }

        public void setMaxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
        }

        public long getMaxWallTimeMs() {
            return maxWallTimeMs;
        }

        public void setMaxWallTimeMs(long maxWallTimeMs) {
            this.maxWallTimeMs = maxWallTimeMs;
        }
    }

    public static class Chat {

        private final ToolAssisted toolAssisted = new ToolAssisted();

        public ToolAssisted getToolAssisted() {
            return toolAssisted;
        }
    }

    public static class ToolAssisted {

        private int maxToolCalls = 4;
        private int maxSearchCalls = 2;
        private int maxFetchCalls = 2;
        private int minVerifiedSources = 1;
        private long timeoutMs = 30_000L;

        public int getMaxToolCalls() {
            return maxToolCalls;
        }

        public void setMaxToolCalls(int maxToolCalls) {
            this.maxToolCalls = maxToolCalls;
        }

        public int getMaxSearchCalls() {
            return maxSearchCalls;
        }

        public void setMaxSearchCalls(int maxSearchCalls) {
            this.maxSearchCalls = maxSearchCalls;
        }

        public int getMaxFetchCalls() {
            return maxFetchCalls;
        }

        public void setMaxFetchCalls(int maxFetchCalls) {
            this.maxFetchCalls = maxFetchCalls;
        }

        public int getMinVerifiedSources() {
            return minVerifiedSources;
        }

        public void setMinVerifiedSources(int minVerifiedSources) {
            this.minVerifiedSources = minVerifiedSources;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class UnitAgent {

        private int maxToolCalls = 6;
        private int maxSearchCalls = 3;
        private int maxFetchCalls = 3;
        private int reflectionAfterSearches = 1;
        private int minVerifiedSources = 1;
        private long timeoutMs = 120_000L;

        public int getMaxToolCalls() {
            return maxToolCalls;
        }

        public void setMaxToolCalls(int maxToolCalls) {
            this.maxToolCalls = maxToolCalls;
        }

        public int getMaxSearchCalls() {
            return maxSearchCalls;
        }

        public void setMaxSearchCalls(int maxSearchCalls) {
            this.maxSearchCalls = maxSearchCalls;
        }

        public int getMaxFetchCalls() {
            return maxFetchCalls;
        }

        public void setMaxFetchCalls(int maxFetchCalls) {
            this.maxFetchCalls = maxFetchCalls;
        }

        public int getReflectionAfterSearches() {
            return reflectionAfterSearches;
        }

        public void setReflectionAfterSearches(int reflectionAfterSearches) {
            this.reflectionAfterSearches = reflectionAfterSearches;
        }

        public int getMinVerifiedSources() {
            return minVerifiedSources;
        }

        public void setMinVerifiedSources(int minVerifiedSources) {
            this.minVerifiedSources = minVerifiedSources;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class Debug {

        private boolean logPrompts = false;
        private boolean logAgentFlow = true;
        private boolean logModelResponses = true;

        public boolean isLogPrompts() {
            return logPrompts;
        }

        public void setLogPrompts(boolean logPrompts) {
            this.logPrompts = logPrompts;
        }

        public boolean isLogAgentFlow() {
            return logAgentFlow;
        }

        public void setLogAgentFlow(boolean logAgentFlow) {
            this.logAgentFlow = logAgentFlow;
        }

        public boolean isLogModelResponses() {
            return logModelResponses;
        }

        public void setLogModelResponses(boolean logModelResponses) {
            this.logModelResponses = logModelResponses;
        }
    }

    public static class Async {

        private String mode = "local";
        private final Rocketmq rocketmq = new Rocketmq();

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public Rocketmq getRocketmq() {
            return rocketmq;
        }
    }

    public static class Rocketmq {

        private String nameServer = "127.0.0.1:9876";
        private String producerGroup = "platform-producer";
        private String taskTopic = "platform_tasks_dispatch";
        private String taskConsumerGroup = "platform-task-consumer";
        private String memoryTopic = "platform_memory_events";
        private String memoryConsumerGroup = "platform-memory-consumer";
        private String longTermMemoryTopic = "platform_memory_long_term_jobs";
        private String longTermMemoryConsumerGroup = "platform-long-term-memory-consumer";

        public String getNameServer() {
            return nameServer;
        }

        public void setNameServer(String nameServer) {
            this.nameServer = nameServer;
        }

        public String getProducerGroup() {
            return producerGroup;
        }

        public void setProducerGroup(String producerGroup) {
            this.producerGroup = producerGroup;
        }

        public String getTaskTopic() {
            return taskTopic;
        }

        public void setTaskTopic(String taskTopic) {
            this.taskTopic = taskTopic;
        }

        public String getTaskConsumerGroup() {
            return taskConsumerGroup;
        }

        public void setTaskConsumerGroup(String taskConsumerGroup) {
            this.taskConsumerGroup = taskConsumerGroup;
        }

        public String getMemoryTopic() {
            return memoryTopic;
        }

        public void setMemoryTopic(String memoryTopic) {
            this.memoryTopic = memoryTopic;
        }

        public String getMemoryConsumerGroup() {
            return memoryConsumerGroup;
        }

        public void setMemoryConsumerGroup(String memoryConsumerGroup) {
            this.memoryConsumerGroup = memoryConsumerGroup;
        }

        public String getLongTermMemoryTopic() {
            return longTermMemoryTopic;
        }

        public void setLongTermMemoryTopic(String longTermMemoryTopic) {
            this.longTermMemoryTopic = longTermMemoryTopic;
        }

        public String getLongTermMemoryConsumerGroup() {
            return longTermMemoryConsumerGroup;
        }

        public void setLongTermMemoryConsumerGroup(String longTermMemoryConsumerGroup) {
            this.longTermMemoryConsumerGroup = longTermMemoryConsumerGroup;
        }
    }

    public static class Memory {

        private final ShortTerm shortTerm = new ShortTerm();
        private final LongTerm longTerm = new LongTerm();
        private final Redis redis = new Redis();

        public ShortTerm getShortTerm() {
            return shortTerm;
        }

        public Redis getRedis() {
            return redis;
        }

        public LongTerm getLongTerm() {
            return longTerm;
        }
    }

    public static class ShortTerm {

        private int windowSize = 20;
        private boolean readModelAsync = true;
        private long projectorDebounceMs = 1000;
        private final Summary summary = new Summary();

        public int getWindowSize() {
            return windowSize;
        }

        public void setWindowSize(int windowSize) {
            this.windowSize = windowSize;
        }

        public boolean isReadModelAsync() {
            return readModelAsync;
        }

        public void setReadModelAsync(boolean readModelAsync) {
            this.readModelAsync = readModelAsync;
        }

        public long getProjectorDebounceMs() {
            return projectorDebounceMs;
        }

        public void setProjectorDebounceMs(long projectorDebounceMs) {
            this.projectorDebounceMs = projectorDebounceMs;
        }

        public Summary getSummary() {
            return summary;
        }
    }

    public static class Summary {

        private boolean enabled = true;
        private String provider = "gemini";
        private String model = "gemini-3-flash-preview";
        private int maxMessagesPerChunk = 12;
        private int maxCharsPerChunk = 6000;
        private int maxWords = 180;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getMaxMessagesPerChunk() {
            return maxMessagesPerChunk;
        }

        public void setMaxMessagesPerChunk(int maxMessagesPerChunk) {
            this.maxMessagesPerChunk = maxMessagesPerChunk;
        }

        public int getMaxCharsPerChunk() {
            return maxCharsPerChunk;
        }

        public void setMaxCharsPerChunk(int maxCharsPerChunk) {
            this.maxCharsPerChunk = maxCharsPerChunk;
        }

        public int getMaxWords() {
            return maxWords;
        }

        public void setMaxWords(int maxWords) {
            this.maxWords = maxWords;
        }
    }

    public static class Redis {

        private boolean enabled = false;
        private long ttlHours = 24;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getTtlHours() {
            return ttlHours;
        }

        public void setTtlHours(long ttlHours) {
            this.ttlHours = ttlHours;
        }
    }

    public static class LongTerm {

        private String extractionProvider;
        private String extractionModel;
        private String extractionVersion = "v1";
        private int maxAttempts = 3;
        private int turnInterval = 5;
        private int maxContextMessages = 24;

        public String getExtractionProvider() {
            return extractionProvider;
        }

        public void setExtractionProvider(String extractionProvider) {
            this.extractionProvider = extractionProvider;
        }

        public String getExtractionModel() {
            return extractionModel;
        }

        public void setExtractionModel(String extractionModel) {
            this.extractionModel = extractionModel;
        }

        public String getExtractionVersion() {
            return extractionVersion;
        }

        public void setExtractionVersion(String extractionVersion) {
            this.extractionVersion = extractionVersion;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public int getTurnInterval() {
            return turnInterval;
        }

        public void setTurnInterval(int turnInterval) {
            this.turnInterval = turnInterval;
        }

        public int getMaxContextMessages() {
            return maxContextMessages;
        }

        public void setMaxContextMessages(int maxContextMessages) {
            this.maxContextMessages = maxContextMessages;
        }
    }
}

package com.xg.platform.api.tooling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.api.skill.SkillConfigService;
import com.xg.platform.tooling.application.BuiltinToolExecutor;
import com.xg.platform.tooling.application.CliToolExecutor;
import com.xg.platform.tooling.application.McpToolExecutor;
import com.xg.platform.tooling.domain.ToolDescriptor;
import com.xg.platform.tooling.domain.ToolExecutionRequest;
import com.xg.platform.tooling.domain.ToolExecutionResult;
import com.xg.platform.tooling.domain.ToolGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultAgentToolService implements AgentToolService {

    private static final Logger logger = Logger.getLogger(DefaultAgentToolService.class.getName());

    private final CliToolExecutor cliToolExecutor;
    private final BuiltinToolExecutor builtinToolExecutor;
    private final McpToolExecutor mcpToolExecutor;
    private final ObjectMapper objectMapper;
    private final boolean logAgentFlow;
    private final SkillConfigService skillConfigService;

    public DefaultAgentToolService(CliToolExecutor cliToolExecutor,
                                   BuiltinToolExecutor builtinToolExecutor,
                                   McpToolExecutor mcpToolExecutor,
                                   ObjectMapper objectMapper,
                                    boolean logAgentFlow) {
        this(cliToolExecutor, builtinToolExecutor, mcpToolExecutor, objectMapper, logAgentFlow, null);
    }

    public DefaultAgentToolService(CliToolExecutor cliToolExecutor,
                                   BuiltinToolExecutor builtinToolExecutor,
                                   McpToolExecutor mcpToolExecutor,
                                   ObjectMapper objectMapper,
                                   boolean logAgentFlow,
                                   SkillConfigService skillConfigService) {
        this.cliToolExecutor = cliToolExecutor;
        this.builtinToolExecutor = builtinToolExecutor;
        this.mcpToolExecutor = mcpToolExecutor;
        this.objectMapper = objectMapper;
        this.logAgentFlow = logAgentFlow;
        this.skillConfigService = skillConfigService;
    }

    @Override
    public List<ToolDescriptor> listAvailableTools(String userId) {
        List<ToolDescriptor> tools = new ArrayList<>();
        tools.add(new ToolDescriptor(
                "extract_document",
                "Extract readable text from a supported document path.",
                requiredStringSchema("input_path", "Absolute path to the input document."),
                ToolGroup.DOCUMENTS,
                "python-cli"
        ));
        tools.add(new ToolDescriptor(
                "render_pdf_pages",
                "Render PDF pages into PNG files under an output directory.",
                requiredStringSchema("input_path", "Absolute path to the PDF file.", "output_dir", "Absolute path to the output directory."),
                ToolGroup.DOCUMENTS,
                "python-cli"
        ));
        tools.add(new ToolDescriptor(
                "list_workspace_documents",
                "List the documents available in the current workspace or selected document scope. Use this first to confirm what documents are readable before searching or reading them.",
                emptyObjectSchema(),
                ToolGroup.DOCUMENTS,
                "builtin"
        ));
        tools.add(new ToolDescriptor(
                "inspect_document",
                "Inspect one workspace document and return its status, type, page count, chunk count, and a short section preview. Use this when you need to understand document structure before searching or reading.",
                requiredStringSchema("documentId", "The workspace documentId to inspect."),
                ToolGroup.DOCUMENTS,
                "builtin"
        ));
        tools.add(new ToolDescriptor(
                "list_document_sections",
                "List the ordered sections or reading regions for one readable document. Use this to navigate a document by chapter or page region before reading it in detail.",
                requiredStringSchema("documentId", "The workspace documentId to inspect."),
                ToolGroup.DOCUMENTS,
                "builtin"
        ));
        tools.add(new ToolDescriptor(
                "search_document",
                "Search the current document scope for relevant excerpts. Use this to locate answers or candidate pages before calling read_document. It returns matches with page ranges and snippets, not a final answer.",
                searchDocumentSchema(),
                ToolGroup.DOCUMENTS,
                "builtin"
        ));
        tools.add(new ToolDescriptor(
                "read_document",
                "Read a contiguous window of text from one readable document, either from the start, by page range, or by cursor for continued reading. Use this when search results are partial and you need more surrounding evidence.",
                readDocumentSchema(),
                ToolGroup.DOCUMENTS,
                "builtin"
        ));
        tools.add(new ToolDescriptor(
                "write_workspace_note",
                "Write a markdown note into the thread workspace.",
                writeWorkspaceNoteSchema(),
                ToolGroup.WORKSPACE,
                "builtin"
        ));
        tools.add(new ToolDescriptor(
                "load_skill",
                "Load the full body, metadata, and package commands of a skill by skillId when its detailed workflow is needed.",
                requiredStringSchema("skillId", "The skill identifier to load."),
                ToolGroup.WORKSPACE,
                "builtin"
        ));
        tools.add(new ToolDescriptor(
                "load_skill_resource",
                "Read a declared bundled resource from a skill package, such as a references document.",
                loadSkillResourceSchema(),
                ToolGroup.WORKSPACE,
                "builtin"
        ));
        tools.add(new ToolDescriptor(
                "run_skill_command",
                "Run a discovered command from a loaded skill package. Use after checking the skill package commands via load_skill.",
                runSkillCommandSchema(),
                ToolGroup.WORKSPACE,
                "builtin"
        ));
        tools.add(new ToolDescriptor(
                "skill_process_status",
                "Check whether a background skill package command is still running and inspect its recent log tail.",
                requiredStringSchema("handleId", "The handleId returned by run_skill_command when started in background mode."),
                ToolGroup.WORKSPACE,
                "builtin"
        ));
        tools.add(new ToolDescriptor(
                "stop_skill_process",
                "Stop a background skill package command that was started earlier.",
                requiredStringSchema("handleId", "The handleId returned by run_skill_command when started in background mode."),
                ToolGroup.WORKSPACE,
                "builtin"
        ));
        tools.add(new ToolDescriptor(
                "weather",
                "Get structured current or forecast weather for a location.",
                weatherSchema(),
                ToolGroup.SEARCH,
                "builtin"
        ));
        tools.add(new ToolDescriptor(
                "web_search",
                "Search the web through the platform built-in research provider.",
                webSearchSchema(),
                ToolGroup.SEARCH,
                "builtin"
        ));
        tools.add(new ToolDescriptor(
                "web_fetch",
                "Fetch readable text from a web page URL for research use.",
                requiredStringSchema("url", "Absolute http or https URL to fetch."),
                ToolGroup.SEARCH,
                "builtin"
        ));
        tools.add(new ToolDescriptor(
                "suggest_deep_research",
                "Suggest escalating the current chat request into an explicit deep research workflow.",
                suggestDeepResearchSchema(),
                ToolGroup.WORKSPACE,
                "builtin"
        ));
        tools.add(new ToolDescriptor(
                "ask_clarification",
                "Record a clarification request for the current research flow.",
                askClarificationSchema(),
                ToolGroup.WORKSPACE,
                "builtin"
        ));
        tools.add(new ToolDescriptor(
                "research_reflect",
                "Assess current research coverage, identify evidence gaps, and suggest next research actions.",
                researchReflectSchema(),
                ToolGroup.WORKSPACE,
                "builtin"
        ));
        return List.copyOf(tools);
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        log(() -> "agentToolService execute tool=" + request.tool().name()
                + " source=" + request.tool().source()
                + " thread=" + request.threadId()
                + " run=" + request.runId());
        java.util.Map<String, String> envOverrides = resolveEnvOverrides(request);
        return switch (request.tool().source()) {
            case "builtin" -> builtinToolExecutor.execute(request, envOverrides);
            case "python-cli" -> executeCliTool(request, envOverrides);
            case "mcp" -> mcpToolExecutor.execute(request);
            default -> throw new IllegalArgumentException("Unsupported tool source: " + request.tool().source());
        };
    }

    private java.util.Map<String, String> resolveEnvOverrides(ToolExecutionRequest request) {
        if (skillConfigService == null) {
            return java.util.Map.of();
        }
        java.util.LinkedHashSet<String> skillIds = new java.util.LinkedHashSet<>(request.activeSkillIds());
        String explicitSkillId = request.arguments() == null ? "" : request.arguments().path("skillId").asText("").trim();
        if (!explicitSkillId.isBlank()) {
            skillIds.add(explicitSkillId);
        }
        return skillConfigService.resolveRuntimeEnv(request.userId(), java.util.List.copyOf(skillIds));
    }

    private ToolExecutionResult executeCliTool(ToolExecutionRequest request,
                                               java.util.Map<String, String> envOverrides) {
        JsonNode result = cliToolExecutor.executeJson(request.tool().name(), request.arguments(), envOverrides);
        return new ToolExecutionResult(request.tool().name(), result, false, "ok");
    }

    private ObjectNode requiredStringSchema(String propertyName, String propertyDescription) {
        return requiredStringSchema(propertyName, propertyDescription, null, null);
    }

    private ObjectNode emptyObjectSchema() {
        return baseObjectSchema();
    }

    private ObjectNode requiredStringSchema(String firstName,
                                            String firstDescription,
                                            String secondName,
                                            String secondDescription) {
        ObjectNode schema = baseObjectSchema();
        ObjectNode properties = schema.putObject("properties");
        ObjectNode first = properties.putObject(firstName);
        first.put("type", "string");
        first.put("description", firstDescription);
        schema.putArray("required").add(firstName);
        if (secondName != null && secondDescription != null) {
            ObjectNode second = properties.putObject(secondName);
            second.put("type", "string");
            second.put("description", secondDescription);
            schema.withArray("required").add(secondName);
        }
        return schema;
    }

    private ObjectNode writeWorkspaceNoteSchema() {
        ObjectNode schema = baseObjectSchema();
        ObjectNode properties = schema.putObject("properties");
        ObjectNode relativePath = properties.putObject("relativePath");
        relativePath.put("type", "string");
        relativePath.put("description", "Relative path under the thread workspace.");
        ObjectNode content = properties.putObject("content");
        content.put("type", "string");
        content.put("description", "Markdown content to write.");
        schema.putArray("required").add("content");
        return schema;
    }

    private ObjectNode loadSkillResourceSchema() {
        ObjectNode schema = baseObjectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("skillId")
                .put("type", "string")
                .put("description", "The skill identifier that declares the resource.");
        properties.putObject("resourcePath")
                .put("type", "string")
                .put("description", "The declared resource path, such as references/guide.md.");
        properties.putObject("maxChars")
                .put("type", "integer")
                .put("description", "Optional max characters to return.");
        schema.putArray("required").add("skillId").add("resourcePath");
        return schema;
    }

    private ObjectNode runSkillCommandSchema() {
        ObjectNode schema = baseObjectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("skillId")
                .put("type", "string")
                .put("description", "Skill identifier to run a packaged command from.");
        properties.putObject("command")
                .put("type", "string")
                .put("description", "Command id discovered from the skill package.");
        ObjectNode args = properties.putObject("args");
        args.put("type", "array");
        args.putObject("items").put("type", "string");
        args.put("description", "Optional argv items passed to the packaged command.");
        properties.putObject("stdin")
                .put("type", "string")
                .put("description", "Optional stdin text to send to the command.");
        properties.putObject("background")
                .put("type", "boolean")
                .put("description", "Set true to start the command in background and receive a handleId.");
        schema.putArray("required").add("skillId");
        schema.withArray("required").add("command");
        return schema;
    }

    private ObjectNode searchDocumentSchema() {
        ObjectNode schema = baseObjectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("query")
                .put("type", "string")
                .put("description", "Natural-language search query to match against document chunks.");
        properties.putObject("documentId")
                .put("type", "string")
                .put("description", "Optional single documentId to narrow the search scope.");
        properties.putObject("limit")
                .put("type", "integer")
                .put("description", "Optional max number of matches to return. Smaller limits are better when you only need the strongest evidence.");
        schema.putArray("required").add("query");
        return schema;
    }

    private ObjectNode readDocumentSchema() {
        ObjectNode schema = baseObjectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("documentId")
                .put("type", "string")
                .put("description", "The readable workspace documentId to read from.");
        properties.putObject("cursor")
                .put("type", "string")
                .put("description", "Optional continuation cursor returned by a previous read_document call, formatted like chunk:<order>.");
        properties.putObject("pageStart")
                .put("type", "integer")
                .put("description", "Optional first page to read from when you want a specific page range.");
        properties.putObject("pageEnd")
                .put("type", "integer")
                .put("description", "Optional last page to read through when you want a specific page range.");
        properties.putObject("maxChunks")
                .put("type", "integer")
                .put("description", "Optional max number of chunks to read in this call.");
        schema.putArray("required").add("documentId");
        return schema;
    }

    private ObjectNode webSearchSchema() {
        ObjectNode schema = baseObjectSchema();
        ObjectNode properties = schema.putObject("properties");
        ObjectNode query = properties.putObject("query");
        query.put("type", "string");
        query.put("description", "Search query text.");
        ObjectNode maxResults = properties.putObject("maxResults");
        maxResults.put("type", "integer");
        maxResults.put("description", "Maximum number of results to return, up to 8.");
        schema.putArray("required").add("query");
        return schema;
    }

    private ObjectNode weatherSchema() {
        ObjectNode schema = baseObjectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("location")
                .put("type", "string")
                .put("description", "City or region name for the weather query.");
        properties.putObject("dayOffset")
                .put("type", "integer")
                .put("description", "0 for today, 1 for tomorrow, 2 for the day after tomorrow.");
        properties.putObject("days")
                .put("type", "integer")
                .put("description", "How many forecast days to return, from 1 to 3.");
        schema.putArray("required").add("location");
        return schema;
    }

    private ObjectNode askClarificationSchema() {
        ObjectNode schema = baseObjectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("reason")
                .put("type", "string")
                .put("description", "Why clarification is needed.");
        ObjectNode questions = properties.putObject("questions");
        questions.put("type", "array");
        questions.putObject("items").put("type", "string");
        schema.putArray("required").add("questions");
        return schema;
    }

    private ObjectNode suggestDeepResearchSchema() {
        ObjectNode schema = baseObjectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("reason")
                .put("type", "string")
                .put("description", "Why the current request would benefit from explicit deep research.");
        properties.putObject("suggestedTitle")
                .put("type", "string")
                .put("description", "A short suggested research title.");
        properties.putObject("suggestedBrief")
                .put("type", "string")
                .put("description", "A concise suggested research brief the UI can prefill.");
        schema.putArray("required").add("reason");
        schema.withArray("required").add("suggestedBrief");
        return schema;
    }

    private ObjectNode researchReflectSchema() {
        ObjectNode schema = baseObjectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("topic")
                .put("type", "string")
                .put("description", "The research topic or unit title under review.");
        properties.putObject("query")
                .put("type", "string")
                .put("description", "The current search query or task framing.");
        properties.putObject("evidenceSummary")
                .put("type", "string")
                .put("description", "Concise summary of the evidence collected so far.");
        properties.putObject("sourceCount")
                .put("type", "integer")
                .put("description", "How many source references have been collected so far.");
        properties.putObject("stepIndex")
                .put("type", "integer")
                .put("description", "Current research step index, starting from 1.");
        properties.putObject("totalSteps")
                .put("type", "integer")
                .put("description", "Total number of planned research steps.");
        properties.putObject("focus")
                .put("type", "string")
                .put("description", "What this research step is supposed to deliver.");
        properties.putObject("openQuestions").put("type", "array").putObject("items").put("type", "string");
        properties.putObject("completedFindings").put("type", "array").putObject("items").put("type", "string");
        schema.putArray("required").add("topic");
        schema.withArray("required").add("sourceCount");
        return schema;
    }

    private ObjectNode baseObjectSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        return schema;
    }

    private void log(java.util.function.Supplier<String> messageSupplier) {
        if (logAgentFlow && logger.isLoggable(Level.INFO)) {
            logger.info(messageSupplier);
        }
    }
}

package com.xg.platform.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.checkpoint.PostgresSaver;
import org.bsc.langgraph4j.serializer.StateSerializer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class CheckpointConfiguration {

    private final StateSerializer<InteractionState> interactionStateSerializer;
    private final StateSerializer<ResearchTaskState> researchStateSerializer;
    private final CompileConfig interactionCompileConfig;
    private final CompileConfig researchCompileConfig;

    public CheckpointConfiguration() {
        this(
                new PlatformJacksonStateSerializer<>(InteractionState::new),
                new PlatformJacksonStateSerializer<>(ResearchTaskState::new),
                new MemorySaver(),
                new MemorySaver()
        );
    }

    public CheckpointConfiguration(DataSource dataSource) {
        this(dataSource, null);
    }

    public CheckpointConfiguration(DataSource dataSource, ObjectMapper objectMapper) {
        this(
                new PlatformJacksonStateSerializer<>(InteractionState::new, objectMapper),
                new PlatformJacksonStateSerializer<>(ResearchTaskState::new, objectMapper),
                buildSaver(requirePostgresDataSource(dataSource),
                        new PlatformJacksonStateSerializer<>(InteractionState::new, objectMapper)),
                buildSaver(requirePostgresDataSource(dataSource),
                        new PlatformJacksonStateSerializer<>(ResearchTaskState::new, objectMapper))
        );
    }

    public CheckpointConfiguration(MemorySaver interactionSaver, MemorySaver researchSaver) {
        this(
                new PlatformJacksonStateSerializer<>(InteractionState::new),
                new PlatformJacksonStateSerializer<>(ResearchTaskState::new),
                interactionSaver,
                researchSaver
        );
    }

    public CheckpointConfiguration(StateSerializer<InteractionState> interactionStateSerializer,
                                   StateSerializer<ResearchTaskState> researchStateSerializer,
                                   MemorySaver interactionSaver,
                                   MemorySaver researchSaver) {
        this.interactionStateSerializer = interactionStateSerializer;
        this.researchStateSerializer = researchStateSerializer;
        this.interactionCompileConfig = CompileConfig.builder()
                .checkpointSaver(interactionSaver)
                .releaseThread(false)
                .build();
        this.researchCompileConfig = CompileConfig.builder()
                .checkpointSaver(researchSaver)
                .releaseThread(false)
                .build();
    }

    public CompileConfig interactionCompileConfig() {
        return interactionCompileConfig;
    }

    public CompileConfig researchCompileConfig() {
        return researchCompileConfig;
    }

    public StateSerializer<InteractionState> interactionStateSerializer() {
        return interactionStateSerializer;
    }

    public StateSerializer<ResearchTaskState> researchStateSerializer() {
        return researchStateSerializer;
    }

    private static MemorySaver buildSaver(DataSource dataSource,
                                          StateSerializer<?> stateSerializer) {
        try {
            return PostgresSaver.builder()
                    .datasource(dataSource)
                    .stateSerializer(stateSerializer)
                    .createTables(true)
                    .build();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize LangGraph4j Postgres saver", exception);
        }
    }

    private static DataSource requirePostgresDataSource(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalStateException("LangGraph4j checkpoint persistence requires a PostgreSQL DataSource");
        }
        if (!isPostgres(dataSource)) {
            throw new IllegalStateException("LangGraph4j checkpoint persistence requires PostgreSQL");
        }
        return dataSource;
    }

    private static boolean isPostgres(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData()
                    .getDatabaseProductName()
                    .toLowerCase()
                    .contains("postgres");
        } catch (SQLException exception) {
            return false;
        }
    }
}

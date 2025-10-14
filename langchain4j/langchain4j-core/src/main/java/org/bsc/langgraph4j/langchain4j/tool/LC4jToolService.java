package org.bsc.langgraph4j.langchain4j.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.tool.ToolExecutor;
import org.bsc.langgraph4j.action.Command;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.bsc.langgraph4j.agent.ToolResponseBuilder.COMMAND_RESULT;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;

public final  class LC4jToolService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LC4jToolService.class);

    /**
     * Tool specification data class
     * @param value
     * @param executor
     * @deprecated use {@link Map.Entry<ToolSpecification, ToolExecutor>}  instead
     */
    @Deprecated
    public record Specification(ToolSpecification value, ToolExecutor executor)  {
        public static LC4jToolService.Specification of(ToolSpecification value, ToolExecutor executor) {
            return new LC4jToolService.Specification(
                    Objects.requireNonNull(value, "value cannot be null"),
                    Objects.requireNonNull(executor, "executor cannot be null"));
        }
    }

    /**
     * Builder for {@link LC4jToolService}
     */
    public static class Builder extends LC4jToolMapBuilder<Builder> {

        /**
         * Adds a tool specification to the node
         *
         * @param spec     the tool specification
         * @param executor the executor to use
         * @return the builder
         */
        @Deprecated
        public Builder specification(ToolSpecification spec, ToolExecutor executor) {
            return super.tool(spec, executor);
        }

        /**
         * Adds a tool specification to the node
         *
         * @param toolSpecification the tool specification
         * @return the builder
         */
        @Deprecated
        public Builder specification(LC4jToolService.Specification toolSpecification) {
            return super.tool( toolSpecification.value(), toolSpecification.executor());
        }

        /**
         * Adds all the methods annotated with {@link Tool} to the node
         *
         * @param objectWithTools the object containing the tools
         * @return the builder
         */
        @Deprecated
        public Builder specification(Object objectWithTools) {
            return super.toolsFromObject( objectWithTools );
        }

        /**
         * Builds the node
         *
         * @return the node
         */
        public LC4jToolService build() {
            return new LC4jToolService(toolMap());
        }
    }

    public static LC4jToolService.Builder builder() {
        return new LC4jToolService.Builder();
    }

    private final Map<ToolSpecification, ToolExecutor> toolMap;

    public LC4jToolService(  Map<ToolSpecification, ToolExecutor> toolMap ) {
        this.toolMap = Objects.requireNonNull(toolMap, "toolMap cannot be null");
        if (toolMap.isEmpty()) {
            log.warn( "tool chain is empty!" );
            // throw new IllegalArgumentException("entries cannot be empty!");
        }
    }

    /**
     * Returns a list of {@link ToolSpecification}s that can be executed by this node
     *
     * @return a list of tool specifications
     */
    public List<ToolSpecification> toolSpecifications() {
        return this.toolMap.keySet().stream().toList();
    }


    public CompletableFuture<Command> execute(List<ToolExecutionRequest> requests, InvocationContext context, String propertyNameToUpdate ) {
        requireNonNull(requests, "requests cannot be null");
        requireNonNull(propertyNameToUpdate, "propertyNameToUpdate cannot be null");
        requireNonNull( context, "context cannot be null");

        log.trace("execute: {}", requests.stream().map( ToolExecutionRequest::name ).toList() );

        var toolResponses = new ArrayList<ToolExecutionResultMessage>(requests.size());
        Map<String,Object> update = Map.of();
        String gotoNode = null;

        for( var request : requests ) {

            var optionalResult = scopedToolCall(request, context);

            if (optionalResult.isEmpty()) {
                log.warn("tool '{}' not found!", request.name());
                continue;
            }

            var command = optionalResult.get().command();

            if (command.gotoNodeSafe().isPresent()) {
                if (gotoNode != null) {
                    return failedFuture(new IllegalStateException(format("Multiple nodes target provided! tried to set %s when %s was already present : ",
                            command.gotoNode(),
                            gotoNode)));
                }
                gotoNode = command.gotoNode();
            }

            update = mergeMap( update, command.update(), (v1,v2) -> v2  );

            toolResponses.add( optionalResult.get().toolResultMessage() );

        }

        update = mergeMap( update, Map.of(propertyNameToUpdate, toolResponses ) );

        return completedFuture( new Command( gotoNode, update  ) );
    }

    private record ScopedToolCallResult(
            ToolExecutionResultMessage toolResultMessage,
            Command command
    ){
        public Command command() {
            return ofNullable(command).orElseGet(Command::emptyCommand);
        }

        ScopedToolCallResult {
            requireNonNull( toolResultMessage, "response cannot be null");
        }
    }

    private Optional<ScopedToolCallResult> scopedToolCall(ToolExecutionRequest request,
                                                InvocationContext toolContext )
    {
        final var scopedCommandResult = new AtomicReference<Command>();

        return toolMap.entrySet().stream()
            .filter(e -> Objects.equals(e.getKey().name(),request.name()))
            .map(Map.Entry::getValue)
            .findFirst()
            .map(e -> {

                final var contextMapData = mergeMap(
                        ofNullable(toolContext.invocationParameters())
                                .map( InvocationParameters::asMap )
                                .orElseGet(Map::of),
                        Map.of(COMMAND_RESULT, scopedCommandResult),
                        (v1, v2) -> v2);

                final var newToolContext = InvocationContext.builder()
                        .chatMemoryId(toolContext.chatMemoryId())
                        .invocationParameters( InvocationParameters.from(contextMapData) )
                        .build();

                return e.executeWithContext(request, newToolContext);
            })
            .map( result -> new ToolExecutionResultMessage(request.id(), request.name(), result.resultText()) )
            .map( toolResultMessage -> new ScopedToolCallResult( toolResultMessage, scopedCommandResult.get() ) );
    }


}
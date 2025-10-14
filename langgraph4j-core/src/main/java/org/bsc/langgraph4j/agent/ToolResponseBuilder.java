package org.bsc.langgraph4j.agent;

import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.utils.TypeRef;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * A builder class to construct a {@link Command} and update the execution context.
 * <p>
 * This builder is used within a tool/action to specify the next node to execute ({@link #gotoNode(String)})
 * and any state updates ({@link #update(Map)}). The resulting {@link Command} is then
 * placed into the shared context, typically within an {@link AtomicReference}, for the
 * graph execution engine to process.
 */
public class ToolResponseBuilder {
    public static final String COMMAND_RESULT = "AtomicReference<Command>";

    private final Map<String,Object> context;
    private String gotoNode;
    private Map<String,Object> update;
    
    /**
     * Constructs a new ToolResponseBuilder.
     *
     * @param context the shared execution context map
     */
    public ToolResponseBuilder(Map<String,Object> context ) {
        this.context = requireNonNull(context, "context cannot be null!");
    }

    /**
     * Specifies the name of the next node to transition to in the graph.
     *
     * @param gotoNode the name of the target node
     * @return this builder instance for chaining
     */
    public final ToolResponseBuilder gotoNode(String gotoNode) {
        this.gotoNode = gotoNode;
        return this;
    }

    /**
     * Provides a map of key-value pairs to update the shared state.
     *
     * @param update a map containing state updates
     * @return this builder instance for chaining
     */
    public final ToolResponseBuilder update(Map<String, Object> update) {
        this.update = update;
        return this;
    }

    /**
     * Builds the {@link Command}, updates the context, and returns the provided result.
     *
     * @param result the result to return from the tool/action method
     * @param <T> the type of the result
     * @return the provided result, allowing for a fluent return from the calling method
     * @deprecated Use {@link #buildAndReturn(Object)} instead. This method will be removed in a future version.
     */
    @Deprecated(forRemoval = true)
    public final <T> T build( T result ) {
        return buildAndReturn( result );
    }

    /**
     * Builds the {@link Command}, updates the context, and returns the provided result.
     * This is useful for returning a value from a tool execution while also setting the command.
     *
     * @param result the result to return from the tool/action method
     * @param <T> the type of the result
     * @return the provided result, allowing for a fluent return from the calling method
     */
    public final <T> T buildAndReturn( T result ) {
        buildAndSet();
        return result;
    }

    /**
     * Builds the {@link Command} and sets it in the context.
     * This method finds an {@link AtomicReference<Command>} in the context using the {@code commandResultKey},
     * creates a new {@link Command} from the builder's state, and sets it on the reference.
     *
     * @throws IllegalStateException if the object at {@code commandResultKey} in the context is not of the expected type {@code AtomicReference<Command>}.
     */
    public final void buildAndSet() {

        final var commandResultObject = context.get(COMMAND_RESULT);

        final var commandResultRef = new TypeRef<AtomicReference<Command>>() {};

        var commandResult = commandResultRef.cast( commandResultObject )
                .orElseThrow( () -> new IllegalStateException( format("'%s' property in context is not right type",COMMAND_RESULT )));

        commandResult.set( new Command( gotoNode, update) );
    }


}

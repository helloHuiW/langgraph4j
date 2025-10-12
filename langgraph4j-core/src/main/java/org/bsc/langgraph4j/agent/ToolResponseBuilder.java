package org.bsc.langgraph4j.agent;

import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.utils.TypeRef;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class ToolResponseBuilder {

    private final Map<String,Object> context;
    private final String commandResultKey;
    private String gotoNode;
    private Map<String,Object> update;

    public ToolResponseBuilder(Map<String,Object> context, String commandResultKey ) {
        this.context = requireNonNull(context, "context cannot be null!");
        this.commandResultKey = requireNonNull(commandResultKey, "commandResultKey cannot be null!");
    }

    public final ToolResponseBuilder gotoNode(String gotoNode) {
        this.gotoNode = gotoNode;
        return this;
    }

    public final ToolResponseBuilder update(Map<String, Object> update) {
        this.update = update;
        return this;
    }

    public final <T> T build( T result ) {

        final var commandResultObject = context.get(commandResultKey);

        final var commandResultRef = new TypeRef<AtomicReference<Command>>() {};

        var commandResult = commandResultRef.cast( commandResultObject )
                .orElseThrow( () -> new IllegalStateException( format("'%s' property in context is not right type",commandResultKey )));

        commandResult.set( new Command( gotoNode, update) );

        return result;
    }

}

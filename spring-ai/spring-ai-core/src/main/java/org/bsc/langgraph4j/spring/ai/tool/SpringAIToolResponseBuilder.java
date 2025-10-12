package org.bsc.langgraph4j.spring.ai.tool;

import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.utils.TypeRef;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.bsc.langgraph4j.spring.ai.tool.SpringAIToolService.COMMAND_RESULT;

public class SpringAIToolResponseBuilder {

    private final ToolContext context;
    private String gotoNode;
    private Map<String,Object> update;

    public static SpringAIToolResponseBuilder of(ToolContext context  ) {
        return new SpringAIToolResponseBuilder( context );
    }

    private SpringAIToolResponseBuilder(ToolContext context ) {
        this.context = requireNonNull(context, "context cannot be null!");
    }

    public SpringAIToolResponseBuilder gotoNode(String gotoNode) {
            this.gotoNode = gotoNode;
            return this;
    }

    public SpringAIToolResponseBuilder update(Map<String, Object> update) {
        this.update = update;
        return this;
    }

    public <T> T build( T result ) {

        final var commandResultObject = context
                .getContext()
                .get(COMMAND_RESULT);

        final var commandResultRef = new TypeRef<AtomicReference<Command>>() {};

        var commandResult = commandResultRef.cast( commandResultObject )
                .orElseThrow( () -> new IllegalStateException( format("'%s' property in context is not right type",COMMAND_RESULT )));

        commandResult.set( new Command( gotoNode, update) );

        return result;
    }
}

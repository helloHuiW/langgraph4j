package org.bsc.langgraph4j.langchain4j.tool;

import dev.langchain4j.invocation.InvocationParameters;
import org.bsc.langgraph4j.agent.ToolResponseBuilder;

public class LC4jToolResponseBuilder extends ToolResponseBuilder {

    public static LC4jToolResponseBuilder of(InvocationParameters context  ) {
        return new LC4jToolResponseBuilder( context );
    }

    private LC4jToolResponseBuilder(InvocationParameters  context ) {
        super( context.asMap());
    }

}

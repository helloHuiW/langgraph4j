package org.bsc.langgraph4j.spring.ai.tool;

import org.bsc.langgraph4j.agent.ToolResponseBuilder;
import org.springframework.ai.chat.model.ToolContext;

import static org.bsc.langgraph4j.spring.ai.tool.SpringAIToolService.COMMAND_RESULT;

public class SpringAIToolResponseBuilder extends ToolResponseBuilder {

    public static SpringAIToolResponseBuilder of(ToolContext context  ) {
        return new SpringAIToolResponseBuilder( context );
    }

    private SpringAIToolResponseBuilder(ToolContext context ) {
        super( context.getContext(), COMMAND_RESULT);
    }

}

package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.CollectionsUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import static org.bsc.langgraph4j.utils.CollectionsUtils.lastOf;
import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractAgentExecutorTest {


    protected abstract  StateGraph<AgentExecutor.State> newGraph()  throws Exception ;


    private List<AgentExecutor.State> executeAgent( String prompt )  throws Exception {

        var iterator = newGraph().compile().stream( Map.of( "messages", UserMessage.from(prompt) ) );

        return iterator.stream()
                .peek( s -> System.out.println( s.node() ) )
                .map( NodeOutput::state)
                .collect(Collectors.toList());
    }

    private List<AgentExecutor.State> executeAgent( String prompt,
                                                    String threadId,
                                                    BaseCheckpointSaver saver)  throws Exception
    {

        CompileConfig compileConfig = CompileConfig.builder()
                .checkpointSaver( saver )
                .build();

        var config = RunnableConfig.builder().threadId(threadId).build();

        var graph = newGraph().compile( compileConfig );

        var iterator = graph.stream( Map.of( "messages", UserMessage.from(prompt)), config );

        return iterator.stream()
                .peek( s -> System.out.println( s.node() ) )
                .map( NodeOutput::state)
                .collect(Collectors.toList());
    }

    @Test
    void executeAgentWithSingleToolInvocation() throws Exception {

        var states = executeAgent("what is the result of test with messages: 'MY FIRST TEST'");
        assertEquals( 6, states.size() );
        var state = lastOf(states).orElse(null);
        assertNotNull(state);
        assertTrue(state.finalResponse().isPresent());
        System.out.println(state.finalResponse().get());

    }

    @Test
    void executeAgentWithDoubleToolInvocation() throws Exception {

        var states = executeAgent("what is the result of test with messages: 'MY FIRST TEST' and the result of test with message: 'MY SECOND TEST'");
        assertEquals( 6, states.size() );
        var state = lastOf(states).orElse(null);
        assertNotNull(state);
        assertTrue(state.finalResponse().isPresent());
        System.out.println(state.finalResponse().get());
    }

    @Test
    void executeAgentWithDoubleToolInvocationWithCheckpoint() throws Exception {

        var saver = new MemorySaver();

        CompileConfig compileConfig = CompileConfig.builder()
                .checkpointSaver( saver )
                .build();

        var config = RunnableConfig.builder().
                        threadId("thread_1")
                        .build();

        var graph = newGraph().compile( compileConfig );

        var iterator = graph.stream(
                Map.of( "messages",
                        UserMessage.from("what is the result of test with messages: 'MY FIRST TEST' and the result of test with message: 'MY SECOND TEST'")),
                config );

        var states = iterator.stream()
                .peek( s -> System.out.println( s.node() ) )
                .map( NodeOutput::state)
                .toList();

        assertEquals( 6, states.size() ); // iterations
        var state = lastOf(states).orElse(null);
        assertNotNull(state);
        assertTrue(state.lastMessage().isPresent());
        System.out.printf( "final response: %s\n", state.lastMessage().get());

        //var stateHistory = graph.lastStateOf( config ).orElseThrow();

        iterator = graph.stream(
                Map.of( "messages",
                        UserMessage.from(
                                "what are the results of tests?")),
                config );

        states = iterator.stream()
                .peek( s -> System.out.println( s.node() ) )
                .map( NodeOutput::state)
                .toList();

        assertEquals( 4, states.size() ); // iterations
        state = lastOf(states).orElse(null);
        assertNotNull(state);
        assertTrue(state.lastMessage().isPresent());
        System.out.printf( "final response: %s\n", state.lastMessage().get());

    }

    @Test
    public void getGraphTest() throws Exception {

        var app = new StateGraph<>(AgentState::new)
            .addEdge(START,"agent")
            .addNode( "agent", node_async( state -> Map.of() ))
            .addNode( "action", node_async( state -> Map.of() ))
            .addConditionalEdges(
                    "agent",
                    edge_async(state -> ""),
                    Map.of("continue", "action", "end", END)
            )
            .addEdge("action", "agent")
            .compile();

        var printConditionalEdge = false;

        var plantUml = app.getGraph( GraphRepresentation.Type.PLANTUML, "Agent Executor", printConditionalEdge );

        System.out.println( plantUml.content() );

        var mermaid = app.getGraph( GraphRepresentation.Type.MERMAID, "Agent Executor", printConditionalEdge );

        System.out.println( mermaid.content() );
    }
}

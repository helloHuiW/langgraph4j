package org.bsc.langgraph4j;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

public class CancellationTest {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("test");

    private AsyncNodeAction<MessagesState<String>> _makeWaitingNode( String id ) {
        return node_async(state -> {
                    Thread.sleep(1000);
                    return Map.of("messages", id);
                });
    }


    private CompletableFuture<Void> requestCancelGenerator(AsyncGenerator.Cancellable<?> generator, boolean mayInterruptIfRunning, long timeout, TimeUnit unit ) {

        return CompletableFuture.runAsync( () -> {
            try {
                Thread.sleep(unit.toMillis(timeout));

                log.info("request cancellation of generator mayInterruptIfRunning: {}", mayInterruptIfRunning);
                var result = generator.cancel(mayInterruptIfRunning);
                log.info("cancellation executed with result: {}", result);

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testGraphCancel() throws Exception {

        var workflow = new StateGraph<MessagesState<String>>(MessagesState.SCHEMA, MessagesState::new)
                .addNode("agent_1", _makeWaitingNode("agent_1" ))
                .addNode("agent_2", _makeWaitingNode("agent_2"))
                .addNode("agent_3", _makeWaitingNode("agent_3"))
                .addEdge("agent_1", "agent_2")
                .addEdge("agent_2", "agent_3")
                .addEdge(START, "agent_1")
                .addEdge("agent_3", END)
                .compile();

        //////////////////////////////////////////////////////////////
        // CANCEL TEST USING FOR EACH ASYNC
        //////////////////////////////////////////////////////////////
        {
            var generator = workflow.stream(GraphInput.noArgs(), RunnableConfig.builder().build());

            requestCancelGenerator(generator, true, 50, TimeUnit.MILLISECONDS);

            var futureResult = generator.forEachAsync(output -> {
                log.info("iteration is on: {}", output);
            }).exceptionally(ex -> {
                assertTrue(generator.isCancelled());
                assertInstanceOf(ExecutionException.class, ex.getCause());
                assertInstanceOf(InterruptedException.class, ex.getCause().getCause());
                return "CANCELLED";
            });

            var genericResult = futureResult.get(5, TimeUnit.SECONDS);

            assertTrue(generator.isCancelled());
            assertNotNull(genericResult);
            assertEquals("CANCELLED", genericResult);
        }
        //////////////////////////////////////////////////////////////
        // CANCEL TEST USING ITERATOR
        //////////////////////////////////////////////////////////////
        {
            var generator = workflow.stream(GraphInput.noArgs(), RunnableConfig.builder().build());

            requestCancelGenerator(generator, true, 50, TimeUnit.MILLISECONDS);


            NodeOutput<?> currentOutput = null;

            for (var output : generator) {
                log.info("iteration is on: {}", output);
                currentOutput = output;
            }

            var optionalResult = AsyncGenerator.resultValue(generator);

            assertNotNull(currentOutput);
            assertNotEquals(END, currentOutput.node());
            assertTrue(generator.isCancelled());
            assertTrue(optionalResult.isEmpty());
        }
    }

    @Test
    public void testCompiledSubGraphCancel() throws Exception {

        var subGraph = new StateGraph<MessagesState<String>>(MessagesState.SCHEMA, MessagesState::new)
                    .addNode("NODE3.1", _makeWaitingNode("NODE3.1"))
                    .addNode("NODE3.2", _makeWaitingNode("NODE3.2"))
                    .addNode("NODE3.3", _makeWaitingNode("NODE3.3"))
                    .addNode("NODE3.4", _makeWaitingNode("NODE3.4"))
                    .addEdge(START, "NODE3.1")
                    .addEdge("NODE3.1", "NODE3.2")
                    .addEdge("NODE3.2", "NODE3.3")
                    .addEdge("NODE3.3", "NODE3.4")
                    .addEdge("NODE3.4", END)
                    .compile();

        var parentGraph =  new StateGraph<MessagesState<String>>(MessagesState.SCHEMA, MessagesState::new)
                .addEdge(START, "NODE1" )
                .addNode("NODE1", _makeWaitingNode("NODE1"))
                .addNode("NODE2", _makeWaitingNode("NODE2"))
                .addNode("NODE3", subGraph)
                .addNode("NODE4", _makeWaitingNode("NODE4"))
                .addNode("NODE5", _makeWaitingNode("NODE5"))
                .addEdge("NODE1", "NODE2")
                .addEdge("NODE2", "NODE3")
                .addEdge("NODE3", "NODE4")
                .addEdge("NODE4", "NODE5")
                .addEdge("NODE5", END)
                .compile();

        //////////////////////////////////////////////////////////////////////////////////
        // NO CANCEL TEST
        //////////////////////////////////////////////////////////////////////////////////
        {
            var generator = parentGraph.stream(GraphInput.noArgs(), RunnableConfig.builder().build());

            var output = generator.stream()
                    .peek(out -> log.info("output: {}", out))
                    .reduce((a, b) -> b);

            assertFalse(generator.isCancelled());
            assertTrue(output.isPresent());
            assertTrue(output.get().isEND());
        }

        //////////////////////////////////////////////////////////////////////////////////
        // CANCEL TEST USING FOR EACH ASYNC
        //////////////////////////////////////////////////////////////////////////////////
        {
            var generator = parentGraph.stream(GraphInput.noArgs(), RunnableConfig.builder().build());

            requestCancelGenerator(generator, true, 3, TimeUnit.SECONDS);

            var futureResult = generator.forEachAsync(out -> {
                log.info("iteration is on: {}", out);
            }).exceptionally(ex -> {
                assertTrue(generator.isCancelled());
                assertInstanceOf(ExecutionException.class, ex.getCause());
                assertInstanceOf(InterruptedException.class, ex.getCause().getCause());
                return "CANCELLED";
            });

            var genericResult = futureResult.get(5, TimeUnit.SECONDS);

            assertTrue(generator.isCancelled());
            assertNotNull(genericResult);
            assertEquals("CANCELLED", genericResult);
        }

        //////////////////////////////////////////////////////////////
        // TEST USING ITERATOR
        //////////////////////////////////////////////////////////////
        {
            var generator = parentGraph.stream(GraphInput.noArgs(), RunnableConfig.builder().build());

            requestCancelGenerator(generator, true, 3, TimeUnit.SECONDS);

            NodeOutput<?> currentOutput = null;

            for (var output : generator) {
                log.info("iteration is on: {}", output);
                currentOutput = output;
            }

            var optionalResult = AsyncGenerator.resultValue(generator);

            assertNotNull(currentOutput);
            assertNotEquals(END, currentOutput.node());
            assertTrue(generator.isCancelled());
            assertTrue(optionalResult.isEmpty());
        }
    }

}

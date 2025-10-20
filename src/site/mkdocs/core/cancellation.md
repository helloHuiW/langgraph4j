# LangGraph4j - Graph Execution Cancellation

LangGraph4j provides a powerful mechanism to cancel the execution of a graph, which is particularly useful for long-running processes. This feature is built upon the cancellation capabilities of the `java-async-generator` library.

## Cancelling a Graph Stream

When you execute a graph using the `stream` method, you get an `AsyncGenerator` instance. This generator can be cancelled, allowing you to stop the graph's execution gracefully or immediately.

To cancel a stream, you need to call the `cancel(boolean mayInterruptIfRunning)` method on the generator.

In the example below we test cancellation considering to make a request from a different thread from the main one using the following method

### Consuming the Stream with `forEachAsync`

When you consume the stream using `forEachAsync`, the graph execution runs in a separate thread.

- **`cancel(true)` (Immediate Cancellation):** This will interrupt the execution thread, causing the `CompletableFuture` returned by `forEachAsync` to complete exceptionally with an `InterruptedException`.

- **`cancel(false)` (Graceful Cancellation):** This will let the currently executing node finish, and then stop the execution before starting the next node.

Here is an example from `CancellationTest.java` that demonstrates immediate cancellation:

```java
var generator = workflow.stream(GraphInput.noArgs(), RunnableConfig.builder().build());

// Request cancellation after 500 milliseconds from a new thread different from main one
CompletableFuture.runAsync( () -> {
    try {
        Thread.sleep(500);
        var result = generator.cancel(mayInterruptIfRunning);
        log.info("cancellation executed with result: {}", result);
    } catch (InterruptedException e) {
        throw new RuntimeException(e);
    }
});

var futureResult = generator.forEachAsync(output -> {
    log.info("iteration is on: {}", output);
}).exceptionally(ex -> {
    assertTrue(generator.isCancelled());
    return "CANCELLED";
});

var genericResult = futureResult.get(5, TimeUnit.SECONDS);

assertTrue(generator.isCancelled());
assertEquals("CANCELLED", genericResult);
```

### Consuming the Stream with an Iterator

When you use a `for-each` loop to iterate over the stream, the execution runs on the current thread.

- **`cancel(true)` or `cancel(false)`:** Both will cause the `hasNext()` method of the iterator to return `false`, effectively stopping the loop.

Here is an example from `CancellationTest.java`:

```java
var generator = workflow.stream(GraphInput.noArgs(), RunnableConfig.builder().build());

// Request cancellation after 500 milliseconds from a new thread different from main one
CompletableFuture.runAsync( () -> {
    try {
        Thread.sleep(500);
        var result = generator.cancel(mayInterruptIfRunning);
        log.info("cancellation executed with result: {}", result);
    } catch (InterruptedException e) {
        throw new RuntimeException(e);
    }
});


NodeOutput<?> currentOutput = null;

for (var output : generator) {
    log.info("iteration is on: {}", output);
    currentOutput = output;
}

assertNotNull(currentOutput);
assertNotEquals(END, currentOutput.node());
assertTrue(generator.isCancelled());
```

## Checking for Cancellation

You can check if a stream has been cancelled by calling the `isCancelled()` method on the generator.

```java
if (generator.isCancelled()) {
    // Handle cancellation
}
```

## Cancellation and Subgraphs

Cancellation also works with nested graphs (subgraphs). If you cancel the execution of a parent graph, the cancellation is propagated to any currently executing subgraph.

## Further Reading

The cancellation mechanism is provided by the `java-async-generator` library. For a more in-depth explanation of the underlying cancellation mechanism, please refer to the [CANCELLATION.md](https://github.com/bsorrentino/java-async-generator/blob/main/CANCELLATION.md) document in the `java-async-generator` repository.
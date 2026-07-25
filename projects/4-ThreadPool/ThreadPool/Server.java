package ThreadPool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * ============================================================================
 *  THREAD-POOL SERVER — the big picture
 * ============================================================================
 *
 *  Problem with the pure multithreaded server (../3-MultiThreaded):
 *      it does `new Thread(...).start()` for EVERY client. 10,000 clients =>
 *      10,000 threads. Each thread carries a Thread Control Block (id, state,
 *      CPU registers, priority) and its own stack, so memory usage spikes and
 *      the CPU wastes time context-switching between thousands of threads.
 *      There is no upper bound — the server can be swamped to death.
 *
 *  Fix — a THREAD POOL:
 *      Create a FIXED number of worker threads up front (here: 100) and reuse
 *      them. Incoming client tasks are put on an internal QUEUE. A task is only
 *      picked up when one of the pool's threads becomes idle. So at most 100
 *      clients are actively processed at once; the rest wait in the queue.
 *
 *      -> Memory and context-switching are BOUNDED no matter how many clients
 *         arrive. Threads are recycled instead of created-and-destroyed per
 *         request.
 *
 *  Who does what:
 *      - The main thread still runs the accept() loop.
 *      - Instead of spawning a thread itself, it just SUBMITS a ClientHandler
 *        task to the pool (threadPool.submit(...)). The pool decides which of
 *        its reusable threads runs it, and when.
 *
 *  Progression across the projects:
 *      2-SingleThreaded : 1 thread total, clients wait in line.
 *      3-MultiThreaded  : 1 thread PER client, unbounded -> memory spike.
 *      4-ThreadPool     : N reusable threads + a queue -> bounded & scalable.
 * ============================================================================
 */
public class Server {

    // The fixed number of worker threads in the pool. This caps how many
    // clients are served simultaneously; extra clients wait in the pool's queue.
    private static final int POOL_SIZE = 100;

    public void run() throws IOException {
        int port = 8010;

        // ServerSocket = the single "front door" clients connect to.
        ServerSocket socket = new ServerSocket(port);

        // Create the pool ONCE, before the loop. Executors.newFixedThreadPool
        // builds an ExecutorService backed by POOL_SIZE threads and an
        // unbounded task queue. Those threads are created and then REUSED for
        // every task — we never call `new Thread` per client again.
        ExecutorService threadPool = Executors.newFixedThreadPool(POOL_SIZE);

        System.out.println("Server started with a thread pool of " + POOL_SIZE + " threads");

        try {
            while (true) {
                System.out.println("Server is listening on port " + port);

                // Block until a client connects; get its private socket.
                Socket acceptedConnection = socket.accept();
                System.out.println("Connection accepted from client " + acceptedConnection.getRemoteSocketAddress());

                // ---- THE KEY DIFFERENCE FROM THE MULTITHREADED VERSION ----
                // We do NOT create a thread. We hand the task to the pool and
                // let it schedule the work onto one of its reusable threads.
                //   - If a pool thread is idle -> it runs immediately.
                //   - If all POOL_SIZE threads are busy -> the task WAITS in the
                //     pool's internal queue until a thread frees up.
                // submit() returns right away, so the accept loop keeps going.
                threadPool.submit(new ClientHandler(acceptedConnection));
            }
        } finally {
            // In this demo the loop never ends, but as good practice: shutting
            // the pool down stops its threads once queued tasks are finished.
            threadPool.shutdown();
        }
    }

    public static void main(String[] args) {
        Server server = new Server();
        try {
            server.run();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}

/*
 * ClientHandler = the work for ONE client. Identical idea to the multithreaded
 * version — the ONLY thing that changed is WHO runs it: here it is executed by
 * a REUSED thread borrowed from the pool, not a freshly created one. Note the
 * pool thread names look like "pool-1-thread-N".
 */
class ClientHandler implements Runnable {

    private final Socket connection;

    public ClientHandler(Socket connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        try {
            // A pool thread's name, e.g. "pool-1-thread-3". Because threads are
            // reused, you'll see the same names handle many different clients
            // over time — proof that they are recycled, not recreated.
            String threadName = Thread.currentThread().getName();

            PrintWriter toClient = new PrintWriter(connection.getOutputStream());
            BufferedReader fromClient = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            String fromClientMsg = fromClient.readLine();
            System.out.println("[" + threadName + "] received from client: " + fromClientMsg);

            // Simulate work so overlapping clients are easy to observe.
            Thread.sleep(3000);

            toClient.println("Hello from the server (handled by " + threadName + ")");

            // Close the WRITER: flushes its buffer to the client, then closes
            // the socket. (Closing the raw socket instead would drop the reply.)
            toClient.close();
        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
        }
    }
}

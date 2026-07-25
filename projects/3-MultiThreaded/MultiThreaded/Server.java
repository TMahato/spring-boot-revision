package MultiThreaded;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/*
 * ============================================================================
 *  MULTITHREADED SERVER — the big picture
 * ============================================================================
 *
 *  There are TWO kinds of threads at work here:
 *
 *   1. The "main" / acceptor thread  -> runs run(). Its ONLY job is to sit in a
 *      loop and accept new connections. It does almost no work per client, so
 *      it is free to accept the next client almost instantly.
 *
 *   2. One "worker" thread PER CLIENT -> each accepted connection is wrapped in
 *      a ClientHandler (a Runnable) and handed to its own new Thread. That
 *      thread does the actual, potentially slow, per-client work (reading,
 *      processing, replying).
 *
 *  Why this gives concurrency:
 *      accept() blocks only until a connection arrives. The slow part
 *      (reading + the 3s of "work" + replying) happens on the WORKER thread,
 *      NOT on the main thread. So while Thread-0 is busy serving client A, the
 *      main thread has already looped back and can accept + start Thread-1 for
 *      client B. The workers run at the same time, overlapping their waits.
 *
 *      => 3 clients that each take ~3s finish in ~3s total (parallel),
 *         not ~9s (which is what the single-threaded server would take).
 *
 *  Contrast with the single-threaded server (../2-SingleThreaded): there the
 *  SAME thread that accepts also does all the work, so client B cannot even be
 *  accepted until client A is completely finished.
 * ============================================================================
 */
public class Server {

    // Runs on the MAIN thread. This is the "acceptor loop".
    public void run() throws IOException {
        int port = 8010;

        // ServerSocket = the "front door". It binds to the port and is what
        // clients connect to. There is only ONE of these, shared by everyone.
        ServerSocket socket = new ServerSocket(port);

        while (true) {
            System.out.println("Server is listening on port " + port);

            // accept() BLOCKS here until some client connects. When one does,
            // it returns a brand-new Socket dedicated to THAT client (a private
            // line), while the ServerSocket stays open for future clients.
            Socket acceptedConnection = socket.accept();
            System.out.println("Connection accepted from client " + acceptedConnection.getRemoteSocketAddress());

            // ---- THE KEY MOVE THAT MAKES THIS MULTITHREADED ----
            // Instead of talking to the client right here (which would block
            // this loop), we wrap the connection in a ClientHandler and give it
            // to a NEW thread. start() launches that thread; it runs the
            // handler's run() method independently, in parallel with us.
            Thread worker = new Thread(new ClientHandler(acceptedConnection));
            worker.start();

            // start() returns immediately (it does NOT wait for the worker to
            // finish). So control comes straight back to the top of the loop
            // and we can accept the next client while the worker is still busy.
            // THIS is why many clients are served concurrently.
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
 * ClientHandler = the work done for ONE client, on its OWN thread.
 *
 * It implements Runnable, which means "a chunk of code that can be run on a
 * thread". We create one ClientHandler per accepted connection, so every client
 * gets an independent copy with its own private `connection` socket. Because
 * each runs on a separate thread, several ClientHandlers execute at the same
 * time without interfering with each other.
 */
class ClientHandler implements Runnable {

    // Each handler holds the private socket for the one client it serves.
    // `final` = it is assigned once (in the constructor) and never reassigned.
    private final Socket connection;

    public ClientHandler(Socket connection) {
        this.connection = connection;
    }

    // run() is the code that executes ON the worker thread when start() is
    // called back in the acceptor loop. Everything below happens off the main
    // thread, so it can be slow without stalling the server.
    @Override
    public void run() {
        try {
            // The name of the thread currently executing this code
            // (e.g. "Thread-0", "Thread-1"). We include it in the reply so you
            // can SEE that different clients are handled by different threads.
            String threadName = Thread.currentThread().getName();

            // Streams for talking to this one client over its private socket.
            PrintWriter toClient = new PrintWriter(connection.getOutputStream());
            BufferedReader fromClient = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            // Read one line the client sent. This blocks THIS worker thread
            // only — the main thread and other workers are unaffected.
            String fromClientMsg = fromClient.readLine();
            System.out.println("[" + threadName + "] received from client: " + fromClientMsg);

            // Pretend this request takes 3 seconds of work (e.g. a DB call).
            // If the server were single-threaded, every other client would wait
            // out this full 3s. Here they DON'T: each client's 3s runs on its
            // own thread, so the waits overlap instead of stacking up.
            Thread.sleep(3000);

            // Send the reply, tagged with which thread handled it.
            toClient.println("Hello from the server (handled by " + threadName + ")");

            // Close the WRITER (not just the raw socket): this flushes the
            // PrintWriter's internal buffer down to the client first, THEN
            // closes the connection. Closing the raw socket instead would throw
            // the buffered reply away and the client would read null.
            toClient.close();
        } catch (IOException | InterruptedException ex) {
            // IOException        -> a stream/socket problem.
            // InterruptedException -> Thread.sleep was interrupted.
            ex.printStackTrace();
        }
    }
}

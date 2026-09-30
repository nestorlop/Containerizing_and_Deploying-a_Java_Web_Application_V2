package co.edu.escuelaing;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WebFramework {
    private final Router router = new Router();
    private final StaticFileService staticFileService = new StaticFileService();
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private int port = 8080;
    private String appEnv = "development";
    private ExecutorService executor;

    public void staticfiles(String path) {
    }

    public void get(String path, WebService handler) {
        router.get(path, handler);
    }

    public void start() throws IOException {
        start(port);
    }

    public void start(int port) throws IOException {
        this.port = port;
        this.appEnv = System.getenv().getOrDefault("APP_ENV", "development");

        int threadPoolSize = 10;
        String poolSizeStr = System.getenv("THREAD_POOL_SIZE");
        if (poolSizeStr != null && !poolSizeStr.isEmpty()) {
            try {
                threadPoolSize = Integer.parseInt(poolSizeStr);
            } catch (NumberFormatException e) {
                System.err.println("Invalid THREAD_POOL_SIZE value: " + poolSizeStr + ", using default 10");
            }
        }
        this.executor = Executors.newFixedThreadPool(threadPoolSize);

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

        if ("development".equals(appEnv)) {
            get("/shutdown", (req, resp) -> {
                resp.setContentType("text/plain; charset=utf-8");
                resp.setBody("Server shutting down...\n");
                resp.send();
                stop();
            });
        }

        serverSocket = new ServerSocket(port);
        running = true;

        System.out.println("Server started on port " + port);
        System.out.println("Environment: " + appEnv);
        System.out.println("Static files: /webroot");
        System.out.println("Thread pool size: " + threadPoolSize);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleRequest(clientSocket));
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
        }

        System.out.println("Server stopped.");
    }

    private void handleRequest(Socket clientSocket) {
        try {
            Request request = new Request(clientSocket);
            Response response = new Response(clientSocket.getOutputStream());

            String method = request.getMethod();
            String path = request.getPath();

            WebService handler = router.findHandler(method, path);
            if (handler != null) {
                handler.handle(request, response);
                return;
            }

            if (staticFileService.serve(path, response)) {
                return;
            }

            response.setStatus(404);
            response.setContentType("text/plain; charset=utf-8");
            response.setBody("404 Not Found: " + path);
            response.send();

        } catch (IOException e) {
            try {
                Response errorResponse = new Response(clientSocket.getOutputStream());
                errorResponse.setStatus(400);
                errorResponse.setContentType("text/plain; charset=utf-8");
                errorResponse.setBody("400 Bad Request: " + e.getMessage());
                errorResponse.send();
            } catch (IOException ex) {
            }
            System.err.println("Request handling error: " + e.getMessage());
        } catch (Exception e) {
            try {
                Response errorResponse = new Response(clientSocket.getOutputStream());
                errorResponse.setStatus(500);
                errorResponse.setContentType("text/plain; charset=utf-8");
                errorResponse.setBody("500 Internal Server Error");
                errorResponse.send();
            } catch (IOException ex) {
            }
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
            }
        }
    }

    public void stop() {
        if (!running) {
            return;
        }

        System.out.println("Shutting down server...");
        running = false;

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing server socket: " + e.getMessage());
            }
        }

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    System.err.println("Thread pool did not terminate in time, forcing shutdown...");
                    executor.shutdownNow();
                    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                        System.err.println("Thread pool did not terminate after forced shutdown");
                    }
                }
            } catch (InterruptedException e) {
                System.err.println("Interrupted while waiting for thread pool termination");
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("Server stopped gracefully.");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }
}
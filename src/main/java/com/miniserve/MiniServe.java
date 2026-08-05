package com.miniserve;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * MiniServe — a zero-dependency static file server.
 * Usage: java MiniServe.java [port] [root-dir]
 */
public final class MiniServe {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"));

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        Path root = args.length > 1 ? Path.of(args[1]).toAbsolutePath() : Path.of(".").toAbsolutePath();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> handle(exchange, root));
        server.setExecutor(null);
        server.start();

        System.out.printf("MiniServe listening on http://localhost:%d%n", port);
        System.out.printf("Serving: %s%n", root);
    }

    private static void handle(HttpExchange exchange, Path root) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            sendStatus(exchange, 405, "Method Not Allowed");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        Path file = root.resolve(path.substring(1)).normalize();

        if (!file.startsWith(root)) {
            sendStatus(exchange, 403, "Forbidden");
            return;
        }

        if (Files.isDirectory(file)) {
            file = file.resolve("index.html");
        }

        if (!Files.exists(file) || !Files.isReadable(file)) {
            sendStatus(exchange, 404, "Not Found");
            return;
        }

        String contentType = Files.probeContentType(file);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        long length = Files.size(file);
        String lastModified = HTTP_DATE.format(
                Files.getLastModifiedTime(file).toInstant());

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(length));
        exchange.getResponseHeaders().set("Last-Modified", lastModified);
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
        exchange.getResponseHeaders().set("Server", "MiniServe/1.0");

        if ("HEAD".equals(method)) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        exchange.sendResponseHeaders(200, length);
        try (OutputStream out = exchange.getResponseBody();
             InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
    }

    private static void sendStatus(HttpExchange exchange, int code, String message) throws IOException {
        byte[] body = ("<h1>" + code + " " + message + "</h1>").getBytes();
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private MiniServe() {
        throw new UnsupportedOperationException();
    }
}

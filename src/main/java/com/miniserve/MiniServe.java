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

    private static boolean enableCors = true;
    private static boolean enableDirListing = true;

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        Path root = args.length > 1 ? Path.of(args[1]).toAbsolutePath() : Path.of(".").toAbsolutePath();

        // Parse flags
        for (String arg : args) {
            if ("--no-cors".equals(arg)) enableCors = false;
            if ("--no-dir-listing".equals(arg)) enableDirListing = false;
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> handle(exchange, root));
        server.setExecutor(null);
        server.start();

        System.out.printf("MiniServe listening on http://localhost:%d%n", port);
        System.out.printf("Serving: %s%n", root);
        System.out.printf("CORS: %s | Dir listing: %s%n", enableCors, enableDirListing);
    }

    private static void handle(HttpExchange exchange, Path root) throws IOException {
        String method = exchange.getRequestMethod();

        // Handle CORS preflight
        if ("OPTIONS".equals(method) && enableCors) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(204, -1);
            return;
        }

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
            Path indexFile = file.resolve("index.html");
            if (Files.exists(indexFile) && Files.isReadable(indexFile)) {
                file = indexFile;
            } else if (enableDirListing) {
                sendDirListing(exchange, file, path);
                return;
            } else {
                sendStatus(exchange, 403, "Directory listing disabled");
                return;
            }
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

        if (enableCors) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        }
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(length));
        exchange.getResponseHeaders().set("Last-Modified", lastModified);
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
        exchange.getResponseHeaders().set("Server", "MiniServe/1.1");

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

    private static void sendDirListing(HttpExchange exchange, Path dir, String uriPath) throws IOException {
        StringBuilder html = new StringBuilder(
            "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">" +
            "<title>Index of " + uriPath + "</title>" +
            "<style>body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;" +
            "max-width:720px;margin:2rem auto;padding:0 1rem;color:#24292e}" +
            "a{color:#0969da;text-decoration:none}a:hover{text-decoration:underline}" +
            "tr:hover{background:#f6f8fa}td{padding:4px 8px}" +
            ".size{text-align:right;color:#656d76;font-size:.9em}</style></head>" +
            "<body><h2>Index of " + uriPath + "</h2><table>");

        try (var files = Files.newDirectoryStream(dir)) {
            for (Path entry : files) {
                String name = entry.getFileName().toString();
                String href = (uriPath.endsWith("/") ? uriPath : uriPath + "/") + name;
                if (Files.isDirectory(entry)) {
                    html.append("<tr><td>📁 <a href=\"").append(href).append("/\">").append(name).append("/</a></td><td></td></tr>");
                } else {
                    long size = Files.size(entry);
                    html.append("<tr><td><a href=\"").append(href).append("\">").append(name).append("</a></td>");
                    html.append("<td class=\"size\">").append(formatSize(size)).append("</td></tr>");
                }
            }
        }

        html.append("</table><hr><sub>MiniServe/1.1</sub></body></html>");
        byte[] body = html.toString().getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private MiniServe() {
        throw new UnsupportedOperationException();
    }
}

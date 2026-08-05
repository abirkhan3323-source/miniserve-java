# MiniServe

A zero-dependency static file server in Java. Uses only the standard library
(`com.sun.net.httpserver`).

## Usage

```bash
# Compile and run
javac -d out src/main/java/com/miniserve/MiniServe.java
java -cp out com.miniserve.MiniServe 8080 ./public

# Or with Java 11+ single-file source
java src/main/java/com/miniserve/MiniServe.java 8080 ./public
```

## Features

- Zero external dependencies
- GET and HEAD support
- Automatic MIME type detection
- Directory listing via index.html
- Path traversal protection
- Cache-Control and Last-Modified headers

## Quick Start

```bash
java src/main/java/com/miniserve/MiniServe.java 8080 ./public
```

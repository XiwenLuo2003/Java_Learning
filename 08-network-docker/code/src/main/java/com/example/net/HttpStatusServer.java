package com.example.net;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP 响应的样子，以及 200 / 202 / 401 / 403 / 500 / 502 分别在什么时候用。
 * 直接运行 main：自己起一个端口，自己把每个路径请求一遍，然后停掉。不需要浏览器。
 */
public class HttpStatusServer {

    private static final AtomicInteger TASKS = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8083), 0);
        server.createContext("/ok", exchange -> send(exchange, 200, "ok"));
        server.createContext("/accepted", exchange -> {
            int taskId = TASKS.incrementAndGet();
            long acceptedAt = System.currentTimeMillis();
            // 只做参数检查就返回。真正的工作丢到后面，调用方不用干等
            Thread worker = new Thread(() -> {
                sleep(400);
                System.out.println("   [后台] task " + taskId + " 完成，距离 202 响应 "
                        + (System.currentTimeMillis() - acceptedAt) + "ms");
            });
            worker.setDaemon(true);
            worker.start();
            send(exchange, 202, "accepted task " + taskId);
        });
        server.createContext("/unauthorized", exchange -> send(exchange, 401, "没有登录"));
        server.createContext("/forbidden", exchange -> send(exchange, 403, "登录了，但没有权限"));
        server.createContext("/error", exchange -> send(exchange, 500, "服务器自己的代码抛了异常"));
        server.createContext("/bad-gateway", exchange -> send(exchange, 502, "上游（数据库、下游服务）没有正常响应"));
        server.start();

        System.out.println("1. 一条 HTTP 响应长这样（状态行 + 头 + 空行 + 正文）：");
        System.out.println("""
                   HTTP/1.1 200 OK
                   Content-Type: text/plain; charset=utf-8
                   Content-Length: 2

                   ok
                """);

        HttpClient client = HttpClient.newHttpClient();
        System.out.println("2. 同一个服务上的 6 个状态码：");
        for (String path : new String[]{"/ok", "/accepted", "/unauthorized", "/forbidden", "/error", "/bad-gateway"}) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8083" + path)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.printf("   %-16s %d  %s%n", path, response.statusCode(), response.body());
        }
        sleep(600);
        server.stop(0);
    }

    private static void send(com.sun.net.httpserver.HttpExchange exchange, int status, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

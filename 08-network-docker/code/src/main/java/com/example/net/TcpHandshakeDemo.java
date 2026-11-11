package com.example.net;

/**
 * TCP 三次握手、四次挥手、TIME_WAIT。直接运行 main，不需要网络。
 * 这是状态机说明，不是一个真正的 TCP 协议栈。
 */
public class TcpHandshakeDemo {

    public static void main(String[] args) {
        System.out.println("1. 三次握手（客户端主动连接）");
        step("客户端", "CLOSED → SYN_SENT", "发 SYN，seq=x");
        step("服务端", "LISTEN → SYN_RCVD", "回 SYN+ACK，seq=y，ack=x+1");
        step("客户端", "SYN_SENT → ESTABLISHED", "回 ACK，ack=y+1");
        step("服务端", "SYN_RCVD → ESTABLISHED", "收到这个 ACK，连接建立");
        System.out.println("   为什么不能两次：服务端收到 SYN 就要分配资源。网络上一个迟到的旧 SYN 会让服务端白建一条没人用的连接。");
        System.out.println("   第三次 ACK 让服务端确认“这次 SYN 是现在的”。客户端在发出 ACK 之前就可以带上数据，但通常不这么做。");

        System.out.println("\n2. SYN Flood");
        System.out.println("   攻击者只发 SYN、不回第三次 ACK，服务端的连接停在 SYN_RCVD，半连接队列被占满，正常用户连不上。");
        System.out.println("   缓解：SYN Cookie（不在队列里保存状态，把状态编进自己的 seq）、缩小 SYN_RCVD 超时、限流。");

        System.out.println("\n3. 四次挥手（客户端先关）");
        step("客户端", "ESTABLISHED → FIN_WAIT_1", "发 FIN，表示自己没有数据要发了");
        step("服务端", "ESTABLISHED → CLOSE_WAIT", "回 ACK。应用还要处理完剩余数据，所以不能马上 FIN");
        step("客户端", "FIN_WAIT_1 → FIN_WAIT_2", "收到 ACK，等对方的 FIN");
        step("服务端", "CLOSE_WAIT → LAST_ACK", "应用关闭，发 FIN");
        step("客户端", "FIN_WAIT_2 → TIME_WAIT", "回最后一个 ACK");
        step("服务端", "LAST_ACK → CLOSED", "收到 ACK");
        step("客户端", "TIME_WAIT → CLOSED", "等 2MSL 之后才真正关闭");

        System.out.println("\n4. 为什么主动关闭的一方要等 2MSL");
        System.out.println("   MSL 是报文在网络里的最长存活时间。等两个 MSL：");
        System.out.println("   ① 最后一个 ACK 如果丢了，对方会重发 FIN，自己还在，能再回一次 ACK；");
        System.out.println("   ② 这条连接上迟到的旧报文都会消失，下一次复用同一个四元组（源 IP、源端口、目的 IP、目的端口）时不会串数据。");
        System.out.println("   所以高并发短连接的服务器不要主动关闭，否则大量连接堆在 TIME_WAIT。");
    }

    private static void step(String who, String state, String action) {
        System.out.printf("   %-6s %-32s %s%n", who, state, action);
    }
}

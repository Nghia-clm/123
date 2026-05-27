package com.auction;

import com.auction.network.ServerMain;

public class Main {
    public static void main(String[] args) {
        ServerMain server = new ServerMain(9999);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}
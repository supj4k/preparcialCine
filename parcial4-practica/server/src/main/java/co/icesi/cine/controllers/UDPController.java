package co.icesi.cine.controllers;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import co.icesi.cine.services.ServicesImpl;

public class UDPController {

    private ServicesImpl services;
    private DatagramSocket socket;
    private int port;
    private boolean running;

    public UDPController(ServicesImpl services, int port) {
        this.services = services;
        this.port = port;
    }

    public void startService() {
        try {
            socket = new DatagramSocket(port, InetAddress.getByName("192.168.131.42"));
            running = true;
            System.out.println("UDP Service started on port " + port);
            while (running) {
                byte[] data = new byte[1024];
                DatagramPacket packet = new DatagramPacket(data, data.length);
                socket.receive(packet);

                String message = new String(data);
                String resp = process(message);

                byte[] out = resp.getBytes();
                socket.send(new DatagramPacket(out, out.length, packet.getAddress(), packet.getPort()));
            }
        } catch (Exception e) {
            if (running) {
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        running = false;
        if (socket != null) {
            socket.close();
        }
    }

    public String process(String message) {
        String[] parts = message.split(";");
        switch (parts[0]) {
            case "PING":
                return parts.length == 1 ? "PONG" : "ERROR;INVALID_FORMAT";
            case "FREE":
                return parts.length == 1 ? "FREE;" + services.freeCount() : "ERROR;INVALID_FORMAT";
            default:
                return "ERROR;INVALID_FORMAT";
        }
    }
}

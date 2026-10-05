package co.icesi.cine.controllers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import co.icesi.cine.controllers.dtos.Request;
import co.icesi.cine.controllers.dtos.Response;
import co.icesi.cine.services.CinemaException;
import co.icesi.cine.services.ServicesImpl;

public class TCPController {

    private ServicesImpl services;

    private ServerSocket serverSocket;

    private boolean running;

    private Executor executor;

    private Gson gson;

    public TCPController(ServicesImpl services) {
        this(services, 12345);
    }

    public TCPController(ServicesImpl services, int port) {
        this.services = services;
        try {
            serverSocket = new ServerSocket(port);
            executor = Executors.newFixedThreadPool(5);
            gson = new GsonBuilder().create();
        } catch (Exception e) {
            e.printStackTrace();
        }
        running = true;
    }

    public void startService() {
        System.out.println("TCP Service started on port " + serverSocket.getLocalPort());
        while (running) {
            try {
                executor.execute(new TCPClientHandler(serverSocket.accept(), services));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            serverSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class TCPClientHandler implements Runnable {

        private Socket clientSocket;
        private ServicesImpl services;

        public TCPClientHandler(Socket clientSocket, ServicesImpl services) {
            this.clientSocket = clientSocket;
            this.services = services;
        }

        @Override
        public void run() {
            try {
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

                String line = reader.readLine();
                Request rq = gson.fromJson(line, Request.class);
                Map<String, String> data = rq.data;
                Response response = new Response();
                response.data = new HashMap<>();
                switch (rq.action) {
                    case "GET_SEATS":
                        response.status = "OK";
                        response.data.put("seats", services.getSeats());
                        response.data.put("free", services.freeCount());
                        break;
                    case "RESERVE":
                        int row = Integer.parseInt(data.get("row"));
                        int col = Integer.parseInt(data.get("col"));
                        try {
                            services.reserve(row, col, data.get("user"));
                            response.status = "OK";
                            response.data.put("seats", services.getSeats());
                            response.data.put("free", services.freeCount());
                        } catch (CinemaException e) {
                            response.status = "ERROR";
                            response.data.put("message", e.getMessage());
                        }
                        break;
                    default:
                        break;
                }

                String json = gson.toJson(response);
                writer.write(json);
                writer.newLine();
                writer.flush();
                writer.close();
                reader.close();

                clientSocket.close();
                System.out.println("Client disconnected: " + clientSocket.getInetAddress());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

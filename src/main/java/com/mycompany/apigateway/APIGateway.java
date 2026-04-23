/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.mycompany.apigateway;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class APIGateway {

    private static final int DEFAULT_PORT = 8080;
    private static int authServicePort;
    private static int productServicePort;
    private static int purchaseServicePort;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter the port number to run the Gateway on it: ");
        String input = scanner.nextLine();
        
        Integer portNumber = null;
        try {
            if (input != null && !input.trim().isEmpty())
                portNumber = Integer.valueOf(input);
        } 
        catch (NumberFormatException e) {
            System.out.println("Invalid input detected. Return back to port number " + DEFAULT_PORT);
        }
        
        int gatewayPort = (portNumber != null) ? portNumber : DEFAULT_PORT;

        System.out.println("Enter port number for Authentication Service: ");
        authServicePort = Integer.parseInt(scanner.nextLine());

        System.out.println("Enter port number for Product Service: ");
        productServicePort = Integer.parseInt(scanner.nextLine());

        System.out.println("Enter Port number for Purchase Service: ");
        purchaseServicePort = Integer.parseInt(scanner.nextLine());
        
        //thread pool for serving more than one client at the same time
        ExecutorService threadPool = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket(gatewayPort)) {
            System.out.println("API Gateway is LIVE on port: " + gatewayPort);
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new ClientHandler(clientSocket, authServicePort, productServicePort, purchaseServicePort));
            }
        } 
        catch (IOException e) {
            System.err.println("Gateway Server Error: " + e.getMessage());
        } 
        finally {
            threadPool.shutdown();
            scanner.close();
        }
    }
}

class ClientHandler implements Runnable {
    private Socket clientSocket;
    private int authPort, productPort, purchasePort;

    public ClientHandler(Socket socket, int auth, int prod, int purchase) {
        clientSocket = socket;
        authPort = auth;
        productPort = prod;
        purchasePort = purchase;
    }
    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            //determing the port
            int targetPort = -1;
            if (requestLine.contains("/api/authentication")) {
                targetPort = authPort;
            } 
            else if (requestLine.contains("/api/products")) {
                targetPort = productPort;
            } 
            else if (requestLine.contains("/api/purchases")) {
                targetPort = purchasePort;
            }

            if (targetPort != -1) {
                forwardRequest(targetPort, requestLine, out);
            } 
            else {
                out.println("HTTP/1.1 404 Not Found\r\n\r\nThe requested API route is not recognized by gateway");
            }
        } 
        catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        } 
        finally {
            try { clientSocket.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    //client communicating with other servers
    private void forwardRequest(int port, String request, PrintWriter clientOut) {
        try (Socket serviceSocket = new Socket("bore.pub", port);
             PrintWriter serviceOut = new PrintWriter(serviceSocket.getOutputStream(), true);
             BufferedReader serviceIn = new BufferedReader(new InputStreamReader(serviceSocket.getInputStream()))) {

            //forwarding
            serviceOut.println(request);
            //passing back the response
            String response;
            while ((response = serviceIn.readLine()) != null) {
                clientOut.println("Service Response: " + response);
            }
        }
        catch (IOException e) {
            clientOut.println("HTTP/1.1 502 Bad Gateway\r\n\r\nService at port " + port + " is unreachable.");
        }
    }
}
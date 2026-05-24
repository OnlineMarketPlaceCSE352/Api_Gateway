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
    static int authServicePort = 33795;
    static String authServiceHost = "mbfdp-41-43-85-85.run.pinggy-free.link";
    static int productServicePort = 8082;
    static String productServiceHost = "bore.pub";
    static int purchaseServicePort = 8083;
    static String purchaseServiceHost = "localhost";

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
        System.out.println("Enter host name for Authentication Service: ");
        authServiceHost = scanner.nextLine();

        System.out.println("Enter port number for Product Service: ");
        productServicePort = Integer.parseInt(scanner.nextLine());
        System.out.println("Enter host name for Product Service: ");
        productServiceHost = scanner.nextLine();

        System.out.println("Enter Port number for Purchase Service: ");
        purchaseServicePort = Integer.parseInt(scanner.nextLine());
        System.out.println("Enter host name for Purchase Service: ");
        purchaseServiceHost = scanner.nextLine();

        //thread pool for serving more than one client at the same time
        ExecutorService threadPool = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket(gatewayPort)) {
            System.out.println("API Gateway is LIVE on port: " + gatewayPort);
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new ClientHandler(clientSocket));
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
    private final Socket clientSocket;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            // Determining the port
            int targetPort = -1;
            String targetHost = null;
            if (requestLine.contains("/api/auth")) {
                targetPort = APIGateway.authServicePort;
                targetHost = APIGateway.authServiceHost;
            }
            else if (requestLine.contains("/api/products")) {
                targetPort = APIGateway.productServicePort;
                targetHost = APIGateway.productServiceHost;
            }
            else if (requestLine.contains("/api/purchases")) {
                targetPort = APIGateway.purchaseServicePort;
                targetHost = APIGateway.purchaseServiceHost;
            }

            if (targetPort != -1) {
                forwardRequest(targetHost, targetPort, requestLine, in, out);
            }
            else {
                out.print("HTTP/1.1 404 Not Found\r\n\r\nThe requested API route is not recognized by gateway");
                out.flush();
            }
        }
        catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
        finally {
            try { clientSocket.close(); } catch (IOException e) {
                System.out.println("Error closing client socket: " + e.getMessage());
            }
        }
    }

    // Client communicating with other servers
    private void forwardRequest(String hostname, int port, String requestLine, BufferedReader clientIn, PrintWriter clientOut) {
        try (Socket serviceSocket = new Socket(hostname, port);
             PrintWriter serviceOut = new PrintWriter(serviceSocket.getOutputStream(), true);
             BufferedReader serviceIn = new BufferedReader(new InputStreamReader(serviceSocket.getInputStream()))) {

            serviceOut.print(requestLine + "\r\n");

            String line;
            int contentLength = 0;

            // Read headers
            while ((line = clientIn.readLine()) != null && !line.isEmpty()) {
                serviceOut.print(line + "\r\n");
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
                }
            }
            serviceOut.print("\r\n");
            serviceOut.flush();

            // Read body
            if (contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                clientIn.read(bodyChars, 0, contentLength);
                serviceOut.print(bodyChars);
                serviceOut.flush();
            }

            int responseContentLength = 0;

            // Read response headers
            while ((line = serviceIn.readLine()) != null && !line.isEmpty()) {
                clientOut.print(line + "\r\n");
                if (line.toLowerCase().startsWith("content-length:")) {
                    responseContentLength = Integer.parseInt(line.split(":")[1].trim());
                }
            }
            clientOut.print("\r\n");
            clientOut.flush();

            // Read response body
            if (responseContentLength > 0) {
                char[] respBodyChars = new char[responseContentLength];
                int charsRead = 0;

                while (charsRead < responseContentLength) {
                    int read = serviceIn.read(respBodyChars, charsRead, responseContentLength - charsRead);
                    if (read == -1) break;
                    charsRead += read;
                }
                clientOut.print(respBodyChars);
                clientOut.flush();
            } else {
                while ((line = serviceIn.readLine()) != null) {
                    clientOut.print(line + "\r\n");
                }
                clientOut.flush();
            }

        }
        catch (IOException e) {
            clientOut.print("HTTP/1.1 502 Bad Gateway\r\n\r\nService at port " + port + " is unreachable.");
            clientOut.flush();
        }
    }
}
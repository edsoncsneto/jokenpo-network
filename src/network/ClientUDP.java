package network;

import java.net.*;
import java.io.*;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class ClientUDP {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 9876;
    private static DatagramSocket socket;
    private static InetAddress serverAddress;
    private static Scanner scanner;

    public static void main(String[] args) {
        try {
            socket = new DatagramSocket();
            serverAddress = InetAddress.getByName(SERVER_ADDRESS);
            scanner = new Scanner(System.in);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (!socket.isClosed()) {
                        sendMessage("SAIR");
                        System.out.println("\nVocê saiu do jogo.");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (socket != null && !socket.isClosed()) {
                            sendMessage("SAIR");
                            socket.close();
                        }
                    } catch (IOException e) {
                        System.out.println("Erro ao tentar desconectar.");
                    }
                
                    System.out.println("Cliente desconectado.");
                }
            }));

            System.out.println("Digite seu nome:");
            String playerName = scanner.nextLine();
            sendMessage("NOME:" + playerName);

            System.out.println("Digite OK para iniciar a partida:");
            String input = scanner.nextLine();
            sendMessage("OK");

            while (true) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println(message);

                if (message.startsWith("ENCERRAR")) {
                    System.out.println("O servidor foi encerrado. O jogo será finalizado.");
                    break;
                }                

                if (message.startsWith("Jogador") && message.contains("desconectou")) {
                    System.out.println(message);
                }                

                if (message.equals("INICIAR") || message.startsWith("Nova rodada iniciando")) {
                    String jogada;
                    do {
                        System.out.println("Escolha PEDRA, PAPEL ou TESOURA:");
                        jogada = scanner.nextLine().toUpperCase();
                    } while (!jogada.equals("PEDRA") && !jogada.equals("PAPEL") && !jogada.equals("TESOURA"));

                    sendMessage("JOGADA:" + playerName + ":" + jogada);
                } else if (message.startsWith("PLACAR:")) {
                    System.out.println("Placar atualizado: " + message);
                } else if (message.startsWith("VENCEDOR:")) {
                    System.out.println("Fim do jogo! Desejam jogar novamente? (S/N)");

                    while (true) {
                        if (scanner.hasNextLine()) {
                            input = scanner.nextLine().trim().toUpperCase();
                            if (input.equals("S") || input.equals("N")) {
                                sendMessage(input);
                                break; // Sai do loop ao receber uma resposta válida
                            } else {
                                System.out.println("Resposta inválida. Digite 'S' para continuar ou 'N' para sair.");
                            }
                        }
                    }
                }

            }
        } catch (NoSuchElementException e) {
            System.out.println("O jogo foi interrompido.");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (scanner != null) {
                scanner.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            System.out.println("Cliente finalizado.");
        }
    }

    private static void sendMessage(String message) throws IOException {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, SERVER_PORT);
        socket.send(packet);
    }
}
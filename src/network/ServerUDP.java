package network;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

public class ServerUDP {
    private static final int PORT = 9876;
    private static final int MAX_PLAYERS = 4;
    private static final int MAX_ROUNDS = 1;
    private static Map<String, Integer> playerPorts = new HashMap<>();
    private static Map<String, InetAddress> playerAddresses = new HashMap<>();
    private static Map<String, String> playerMoves = new HashMap<>();
    private static Map<String, Integer> playerScores = new HashMap<>();
    private static Map<String, Integer> roundScores = new HashMap<>(); // Armazena pontuação da rodada
    private static int confirmedPlayers = 0;
    private static int currentRound = 0;
    private static DatagramSocket socket;

    public static void main(String[] args) throws IOException {
        socket = new DatagramSocket(PORT);
        System.out.println("Servidor aguardando jogadores...");

        while (true) {
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            String message = new String(packet.getData(), 0, packet.getLength());
            InetAddress address = packet.getAddress();
            int port = packet.getPort();

            if (message.startsWith("NOME:")) {
                String playerName = message.split(":")[1];
                if (playerAddresses.containsKey(playerName)) {
                    sendMessage(address, port, "ERRO: Nome já utilizado. Escolha outro.");
                    continue;
                }
                playerAddresses.put(playerName, address);
                playerPorts.put(playerName, port);
                playerScores.put(playerName, 0);
                System.out.println("Jogador registrado: " + playerName + " -> " + address + ":" + port);
            } else if (message.equals("OK")) {
                confirmedPlayers++;
                System.out.println("Jogador confirmado: " + address + ":" + port);

                if (confirmedPlayers == MAX_PLAYERS) {
                    currentRound = 0;
                    System.out.println("Todos os jogadores confirmaram. Iniciando o jogo...");
                    broadcast("INICIAR");
                }
            } else if (message.startsWith("JOGADA:")) {
                String[] parts = message.split(":");
                String playerName = parts[1];
                String move = parts[2];

                while (!move.equals("PEDRA") && !move.equals("PAPEL") && !move.equals("TESOURA")) {
                    sendMessage(playerAddresses.get(playerName), playerPorts.get(playerName), "ERRO: Jogada inválida. Escolha novamente.");
                    byte[] newBuffer = new byte[1024];
                    DatagramPacket newPacket = new DatagramPacket(newBuffer, newBuffer.length);
                    socket.receive(newPacket);
                    move = new String(newPacket.getData(), 0, newPacket.getLength());
                }
                sendMessage(playerAddresses.get(playerName), playerPorts.get(playerName), "AGUARGANDO DEMAIS JOGADORES...");
                playerMoves.put(playerName, move);

                if (playerMoves.size() == confirmedPlayers) {
                    processGame();
                }
            } else if (message.equals("SAIR")) {
                removePlayer(port);
            }
        }
    }

    private static void checkDisconnectedPlayers() throws IOException {
        List<String> disconnectedPlayers = new ArrayList<>();

        for (String player : playerAddresses.keySet()) {
            InetAddress address = playerAddresses.get(player);
            int port = playerPorts.get(player);
            try {
                sendMessage(address, port, ""); // Envia uma mensagem para verificar se o jogador ainda está ativo
            } catch (IOException e) {
                disconnectedPlayers.add(player);
            }
        }

        for (String player : disconnectedPlayers) {
            int port = playerPorts.get(player);
            System.out.println("Jogador " + player + " foi desconectado inesperadamente.");
            broadcast("Jogador " + player + " foi desconectado.");
            removePlayer(port);
        }
        for (String player : disconnectedPlayers) {
            System.out.println(player);
        }

    }


    private static void processGame() throws IOException {
        broadcast("Rodada " + (currentRound + 1) + " encerrada.");
        currentRound++;
        compareMoves();
        broadcastScores();
        playerMoves.clear();

        if (currentRound < MAX_ROUNDS) {
            broadcast("Nova rodada iniciando...");
        } else {
            determineWinner();
        }
    }

    private static void determineWinner() throws IOException {
        String winner = Collections.max(playerScores.entrySet(), Map.Entry.comparingByValue()).getKey();
        broadcast("VENCEDOR: " + winner);
        broadcast("Fim do jogo! Desejam jogar novamente? (S/N)");

        int playersReady = 0;
        while (playersReady < MAX_PLAYERS) {
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            String message = new String(packet.getData(), 0, packet.getLength());

            if (message.equals("S")) {
                playersReady++;
            } else if (message.equals("N")) {
                removePlayer(packet.getPort());
            }
            System.out.println(playersReady);
        }

        resetGame();
        broadcast("INICIAR");
    }

    private static void removePlayer(int port) throws IOException {
        String removedPlayer = null;
        for (String player : playerPorts.keySet()) {
            if (playerPorts.get(player).equals(port)) {
                removedPlayer = player;
                break;
            }
        }
        if (removedPlayer != null) {
            playerAddresses.remove(removedPlayer);
            playerPorts.remove(removedPlayer);
            playerScores.remove(removedPlayer);
            confirmedPlayers--;

            if (confirmedPlayers == 0) {
                System.out.println("Todos os jogadores saíram. O jogo será encerrado.");
                socket.close();
                System.exit(0);
            }

            broadcast("Jogador " + removedPlayer + " desconectou.");
        }
    }

    private static void compareMoves() {
        roundScores.clear(); // Resetar pontuação da rodada

        for (String player : playerMoves.keySet()) {
            roundScores.put(player, 0); // Inicializa a pontuação da rodada com 0 pontos
        }

        for (String player1 : playerMoves.keySet()) {
            String move1 = playerMoves.get(player1);
            for (String player2 : playerMoves.keySet()) {
                if (!player1.equals(player2)) {
                    String move2 = playerMoves.get(player2);
                    if ((move1.equals("PEDRA") && move2.equals("TESOURA")) ||
                            (move1.equals("TESOURA") && move2.equals("PAPEL")) ||
                            (move1.equals("PAPEL") && move2.equals("PEDRA"))) {
                        playerScores.put(player1, playerScores.get(player1) + 1);
                        roundScores.put(player1, roundScores.get(player1) + 1); // Incrementa na pontuação da rodada
                    }
                }
            }
        }
    }

    private static void broadcastScores() throws IOException {
        StringBuilder scoreMessage = new StringBuilder("\nDADOS DA RODADA " + currentRound + "\n");
        scoreMessage.append("----------------------------------------------------\n");
        scoreMessage.append("PONTUAÇÃO DA RODADA:\n");

        for (String player : playerScores.keySet()) {
            int roundPoints = roundScores.getOrDefault(player, 0); // Pegamos os pontos da rodada
            String SingularPlural = "";
            if (roundPoints <= 1) {
                SingularPlural = "ponto";
            } else {
                SingularPlural = "pontos";
            }
            scoreMessage.append(player).append(": ").append(roundPoints).append(" " + SingularPlural + " \n");
        }

        scoreMessage.append("\nPLACAR GERAL:\n");
        for (Map.Entry<String, Integer> entry : playerScores.entrySet()) {
            String SingularPlural = "";
            if (entry.getValue() <= 1) {
                SingularPlural = "ponto";
            } else {
                SingularPlural = "pontos";
            }
            scoreMessage.append(entry.getKey()).append(": ").append(entry.getValue()).append(" " + SingularPlural + " \n");
        }

        scoreMessage.append("----------------------------------------------------\n");

        broadcast(scoreMessage.toString());
    }

    private static void sendMessage(InetAddress address, int port, String message) throws IOException {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        socket.send(packet);
    }

    private static void broadcast(String message) throws IOException {
        System.out.println(message);
        for (String playerName : playerAddresses.keySet()) {
            sendMessage(playerAddresses.get(playerName), playerPorts.get(playerName), message);
        }
    }

    private static void resetGame() {
        playerMoves.clear();
        roundScores.clear();
        confirmedPlayers = 0;
        currentRound = 0;
        playerScores.replaceAll((k, v) -> 0);
    }
}

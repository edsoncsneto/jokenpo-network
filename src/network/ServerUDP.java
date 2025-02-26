package network;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

public class ServerUDP {
    private static final int PORT = 9876;
    private static final int MAX_PLAYERS = 4;
    private static final int MAX_ROUNDS = 5;
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

                // IMPEDIR NOVOS JOGADORES APÓS O JOGO COMEÇAR
                if (currentRound > 0) {
                    sendMessage(address, port, "ERRO: Partida em andamento. Aguarde o próximo jogo.");
                    continue;
                }

                // IMPEDIR JOGADORES COM O MESMO NOME
                if (playerAddresses.containsKey(playerName)) {
                    sendMessage(address, port, "ERRO: Nome já utilizado. Escolha outro.");
                    continue;
                }
                playerAddresses.put(playerName, address);
                playerPorts.put(playerName, port);
                playerScores.put(playerName, 0);
                System.out.println("Jogador registrado: " + playerName + " -> " + address + ":" + port);
            } 
            
            // AGUARDA CONFIRMAÇÃO DOS JOGADORES (PLAYERS)
            else if (message.equals("OK")) {
                confirmedPlayers++;
                System.out.println("Jogador confirmado: " + address + ":" + port);

                if (confirmedPlayers == MAX_PLAYERS) {
                    currentRound = 0;
                    System.out.println("Todos os jogadores confirmaram. Iniciando o jogo...");
                    broadcast("INICIAR");
                }
            } 
            
            // VALIDA AS JOGADAS DE CADA JOGADOR (PLAYER)
            else if (message.startsWith("JOGADA:")) {
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
                playerMoves.put(playerName, move);

                // verifica se algum jogador(player) desconectou
                checkDisconnectedPlayers();

                if (playerMoves.size() == playerAddresses.size()) {
                    processGame();
                } else {
                    sendMessage(playerAddresses.get(playerName), playerPorts.get(playerName), "AGUARDANDO DEMAIS JOGADORES...");
                }
            } 
            // REMOVE UM JOGADOR (PLAYER) QUE ENVIAR A MENSAGEM "SAIR"
            else if (message.equals("SAIR")) {
                removePlayer(port);
            }
        }
    }

    // MÉTODO PARA PROCESSAR A RODADA
    private static void processGame() throws IOException {
        // Se restar só um jogador, ele vence automaticamente
        if (playerMoves.size() < 2) {  
            broadcast("Jogadores insuficientes. O jogo será encerrado.");
            determineWinner(); 
            return;
        }

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

    // MÉTODO PARA DETERMINAR O VENCEDOR DA PARTIDA
    private static void determineWinner() throws IOException {
        String winner = Collections.max(playerScores.entrySet(), Map.Entry.comparingByValue()).getKey();
        broadcast("VENCEDOR: " + winner);
        broadcast("Aguardando confirmação de jogadores...");

        int playersReady = 0;
        int expectedPlayers = playerAddresses.size(); // Número real de jogadores conectados
        System.out.println("Aguardando resposta dos jogadores...");

        while (playersReady < expectedPlayers) {
            try {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength()).trim().toUpperCase();

                if (message.equals("S")) {
                    playersReady++;
                } else if (message.equals("N")) {
                    removePlayer(packet.getPort());
                    expectedPlayers--;
                }
            } catch (IOException e) {
                System.out.println("Erro ao receber resposta dos jogadores.");
            }
        }

        // Se houver menos de 4 jogadores, aguardar novos jogadores antes de reiniciar
        if (confirmedPlayers < MAX_PLAYERS) {
            broadcast("Aguardando novos jogadores para iniciar uma nova partida...");
            waitForNewPlayers();
        }

        System.out.println("Todos os jogadores confirmaram. Reiniciando jogo...");
        resetGame();
        broadcast("INICIAR");
    }

    // MÉTODO PARA AGUARDAR UM NOVO JOGADOR, ANTES DE INCIAR UMA NOVA PARTIDA.
    private static void waitForNewPlayers() throws IOException {
        while (confirmedPlayers < MAX_PLAYERS) {
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            String message = new String(packet.getData(), 0, packet.getLength());
    
            if (message.startsWith("NOME:")) {
                String playerName = message.split(":")[1];
    
                if (playerAddresses.containsKey(playerName)) {
                    sendMessage(packet.getAddress(), packet.getPort(), "ERRO: Nome já utilizado. Escolha outro.");
                    continue;
                }
    
                playerAddresses.put(playerName, packet.getAddress());
                playerPorts.put(playerName, packet.getPort());
                playerScores.put(playerName, 0);
                confirmedPlayers++;
                System.out.println("Novo jogador entrou: " + playerName);
            }
        }
    
        broadcast("Todos os jogadores confirmaram. Aguardando confirmação para iniciar nova partida.");
    }

    // MÉTODO PARA REMOVER UM JOGADOR (PLAYER)
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
            playerMoves.remove(removedPlayer);
            confirmedPlayers--;
            
            // Se não restar nenhum jogador, o servidor é encerrado.
            if (confirmedPlayers == 0) {
                System.out.println("Todos os jogadores saíram. O jogo será encerrado.");
                socket.close();
                System.exit(0);
            }

            // Se houver apenas um jogador, ele vence automaticamente.
            if (confirmedPlayers == 1) {
                determineWinner();
                return;
            }

            broadcast("Jogador " + removedPlayer + " desconectou.");

             // Se um jogador saiu no meio da rodada, verificar desconectados e continuar o jogo
            if (currentRound > 0) {
                checkDisconnectedPlayers();
                processGame();
            }
        }
    }

    // MÉTODO PARA VERIFICAR OS JOGADORES CONECTADOS
    private static void checkDisconnectedPlayers() throws IOException {
        List<String> disconnectedPlayers = new ArrayList<>();
    
        for (String player : playerAddresses.keySet()) {
            InetAddress address = playerAddresses.get(player);
            int port = playerPorts.get(player);
            try {
                sendMessage(address, port, ""); // Envia uma mensagem de teste
            } catch (IOException e) {
                disconnectedPlayers.add(player); // Se o envio falhar, o jogador está offline
            }
        }
    
        for (String player : disconnectedPlayers) {
            removePlayer(playerPorts.get(player)); // Remove os jogadores desconectados
        }
    }    

    // MÉTODO PARA COMPARAR OS MOVIMENTOS A CADA RODADA (ROUND)
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

    // MÉTODO PARA ENVIAR OS DADOS DA RODADA
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

    // MÉTODO PARA ENVIAR MENSAGEM AOS JOGADORES (PLAYERS)
    private static void sendMessage(InetAddress address, int port, String message) throws IOException {
        if (socket == null || socket.isClosed()) return;
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

    // MÉTODO PARA RESETAR O JOGO APÓS O FIM DOS ROUNDS
    private static void resetGame() {
        playerMoves.clear();
        roundScores.clear();
        confirmedPlayers = 0;
        currentRound = 0;
        playerScores.replaceAll((k, v) -> 0);
    }
}

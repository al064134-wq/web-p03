import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ClienteChat {

    private static final int PUERTO = 8080;

    private static Socket socket;
    private static BufferedReader entrada;
    private static PrintWriter salida;
    private static Thread hiloLector;
    private static final Scanner teclado = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("Cliente de chat");
        mostrarAyuda();

        try {
            while (true) {
                System.out.print("> ");
                String linea = teclado.nextLine().trim();
                if (linea.isEmpty()) continue;

                if (linea.startsWith("start-conection")) {
                    String[] partes = linea.split("\\s+", 2);
                    if (partes.length < 2) {
                        System.out.println("Uso: start-conection <IP>");
                        continue;
                    }
                    try {
                        iniciarConexion(partes[1]);
                    } catch (IOException e) {
                        System.out.println("No se pudo conectar: " + e.getMessage());
                    }

                } else if (linea.equalsIgnoreCase("salir")) {
                    if (estaConectado()) salida.println("salir");
                    cerrarSilencioso();
                    System.out.println("Sesión finalizada.");
                    break;

                } else if (linea.startsWith("change-userName")) {
                    String[] partes = linea.split("\\s+", 2);
                    if (partes.length < 2) {
                        System.out.println("Uso: change-userName <nuevoNombre>");
                        continue;
                    }
                    cambiarNombre(partes[1]);

                } else if (linea.startsWith("send-msg")) {
                    String[] partes = linea.split("\\s+", 3);
                    if (partes.length < 3) {
                        System.out.println("Uso: send-msg <usuarioDestino> <mensaje>");
                        continue;
                    }
                    enviarPrivado(partes[1], partes[2]);

                } else if (linea.startsWith("global-msg")) {
                    String[] partes = linea.split("\\s+", 2);
                    if (partes.length < 2) {
                        System.out.println("Uso: global-msg <mensaje>");
                        continue;
                    }
                    enviarGlobal(partes[1]);

                } else if (linea.equalsIgnoreCase("help")) {
                    mostrarAyuda();

                } else {
                    if (estaConectado()) {
                        salida.println(linea);
                    } else {
                        System.out.println("Comando desconocido. Escribe 'help' para ver opciones.");
                    }
                }
            }
        } finally {
            cerrarSilencioso();
        }
    }

    // ===== Métodos solicitados (lado cliente) =====

    // start-conection <IP>  (puerto fijo 8080)
    private static void iniciarConexion(String ip) throws IOException {
        if (estaConectado()) {
            System.out.println("Ya estás conectado.");
            return;
        }
        socket = new Socket(ip, PUERTO);
        entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        salida  = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);

        hiloLector = new Thread(() -> {
            try {
                String recibido;
                while ((recibido = entrada.readLine()) != null) {
                    System.out.println("\nServidor: " + recibido);
                    System.out.print("> ");
                }
            } catch (IOException e) {
                System.out.println("\nConexión cerrada.");
            }
        });
        hiloLector.setDaemon(true);
        hiloLector.start();

        System.out.println("Conectado a " + ip + ":" + PUERTO);
        System.out.println("Comandos: change-userName, send-msg, global-msg, salir");
    }

    // change-userName <nuevoNombre>
    private static void cambiarNombre(String nuevoNombre) {
        requerirConexion();
        salida.println("change-userName " + nuevoNombre);
    }

    // send-msg <usuarioDestino> <mensaje>
    private static void enviarPrivado(String usuarioDestino, String mensaje) {
        requerirConexion();
        salida.println("send-msg " + usuarioDestino + " " + mensaje);
    }

    // global-msg <mensaje>
    private static void enviarGlobal(String mensaje) {
        requerirConexion();
        salida.println("global-msg " + mensaje);
    }

    // ===== Utilidades cliente =====

    private static boolean estaConectado() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    private static void requerirConexion() {
        if (!estaConectado()) throw new IllegalStateException("No estás conectado. Usa: start-conection <IP>");
    }

    private static void cerrarSilencioso() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    private static void mostrarAyuda() {
        System.out.println("Comandos disponibles:");
        System.out.println("  start-conection <IP>                # Conecta al servidor (puerto 8080)");
        System.out.println("  change-userName <nuevoNombre>       # Cambia tu nombre");
        System.out.println("  send-msg <usuarioDestino> <mensaje> # Envía un mensaje privado");
        System.out.println("  global-msg <mensaje>                # Envía un mensaje global");
        System.out.println("  salir                               # Cierra la sesión");
    }
}

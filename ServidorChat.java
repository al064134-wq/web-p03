import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ServidorChat {

    private static final int PUERTO = 8080;

    // nombreUsuario -> sesión activa
    private static final ConcurrentHashMap<String, SesionCliente> sesiones = new ConcurrentHashMap<>();
    private static final AtomicInteger contadorUsuarios = new AtomicInteger(1);

    public static void main(String[] args) {
        System.out.println("Servidor escuchando en el puerto " + PUERTO);
        try (ServerSocket servidor = new ServerSocket(PUERTO)) {
            while (true) {
                Socket socket = servidor.accept();
                SesionCliente sesion = new SesionCliente(socket, "usuario" + contadorUsuarios.getAndIncrement());
                new Thread(sesion).start();
            }
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        }
    }

    // ======= Sesión por cliente =======
    static class SesionCliente implements Runnable {
        private final Socket socket;
        private final BufferedReader entrada;
        private final PrintWriter salida;
        private volatile String nombre;

        SesionCliente(Socket socket, String nombreInicial) throws IOException {
            this.socket = socket;
            this.entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.salida  = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);
            this.nombre = nombreInicial;
        }

        @Override
        public void run() {
            sesiones.put(nombre, this);
            enviarLinea("Bienvenido. Tu usuario temporal es '" + nombre + "'.");
            difundirExcepto("Se ha unido al chat: " + nombre, this);

            try {
                String linea;
                while ((linea = entrada.readLine()) != null) {
                    if ("salir".equalsIgnoreCase(linea)) break;
                    procesarComando(linea);
                }
            } catch (IOException ignored) {
            } finally {
                sesiones.remove(nombre);
                difundirExcepto("Ha salido del chat: " + nombre, this);
                try { socket.close(); } catch (IOException ignored) {}
                System.out.println("Cliente desconectado: " + nombre + " desde " + socket.getInetAddress());
            }
        }

        private void procesarComando(String linea) {
            if (linea.startsWith("change-userName")) {
                String[] partes = linea.split("\\s+", 2);
                if (partes.length < 2) {
                    enviarLinea("Uso: change-userName <nuevoNombre>");
                    return;
                }
                String nuevo = partes[1].trim();
                if (nuevo.isEmpty() || nuevo.contains(" ")) {
                    enviarLinea("Nombre inválido. Evita espacios.");
                    return;
                }
                SesionCliente existente = sesiones.get(nuevo);
                if (existente != null && existente != this) {
                    enviarLinea("El nombre '" + nuevo + "' ya está en uso.");
                    return;
                }
                sesiones.remove(nombre);
                String anterior = nombre;
                nombre = nuevo;
                sesiones.put(nombre, this);
                enviarLinea("Tu nombre ahora es: " + nombre);
                difundirExcepto("El usuario " + anterior + " ahora se llama " + nombre, this);

            } else if (linea.startsWith("send-msg")) {
                String[] partes = linea.split("\\s+", 3);
                if (partes.length < 3) {
                    enviarLinea("Uso: send-msg <usuarioDestino> <mensaje>");
                    return;
                }
                String destino = partes[1].trim();
                String cuerpo = partes[2];

                SesionCliente sesionDestino = sesiones.get(destino);
                if (sesionDestino == null) {
                    enviarLinea("Usuario no encontrado: " + destino);
                    return;
                }
                sesionDestino.enviarLinea("[privado de " + nombre + "]: " + cuerpo);
                enviarLinea("Mensaje enviado a " + destino + ": " + cuerpo);
                System.out.println("Privado " + nombre + " -> " + destino + ": " + cuerpo);

            } else if (linea.startsWith("global-msg")) {
                String[] partes = linea.split("\\s+", 2);
                if (partes.length < 2) {
                    enviarLinea("Uso: global-msg <mensaje>");
                    return;
                }
                String cuerpo = partes[1];
                difundirExcepto("[" + nombre + "]: " + cuerpo, this);
                enviarLinea("Mensaje global enviado.");
                System.out.println("Global " + nombre + ": " + cuerpo);

            } else {
                enviarLinea("Comando no reconocido. Usa: change-userName, send-msg, global-msg, salir");
            }
        }

        private void enviarLinea(String texto) {
            synchronized (salida) {
                salida.println(texto);
            }
        }
    }

    // ======= utilidades servidor =======
    private static void difundirExcepto(String texto, SesionCliente excepto) {
        for (SesionCliente sesion : sesiones.values()) {
            if (sesion != excepto) sesion.enviarLinea(texto);
        }
    }
}

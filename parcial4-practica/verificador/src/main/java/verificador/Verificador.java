package verificador;

import co.icesi.cine.controllers.TCPController;
import co.icesi.cine.controllers.UDPController;
import co.icesi.cine.services.CinemaException;
import co.icesi.cine.services.ServicesImpl;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class Verificador {

    private static final PrintStream OUT = System.out;
    private static final PrintStream SILENCIO = new PrintStream(OutputStream.nullOutputStream());
    private static int aprobadas = 0;
    private static int falladas = 0;
    private static final Map<String, int[]> porSeccion = new LinkedHashMap<>();
    private static String seccionActual;

    public static void main(String[] args) {
        System.setOut(SILENCIO);
        System.setErr(SILENCIO);

        ejecutar("1. Logica UDP (UDPController.process)", Verificador::logicaUdp);
        ejecutar("2. Servicios y concurrencia (ServicesImpl)", Verificador::servicios);
        ejecutar("3. Servidor UDP por la red", Verificador::servidorUdp);
        ejecutar("4. Servidor TCP (TCPController)", Verificador::servidorTcp);
        ejecutar("5. Arranque conjunto (Main)", Verificador::arranque);
        ejecutar("6. Cliente UDP (kiosco.KioskClient)", Verificador::clienteKiosco);
        ejecutar("7. Cliente TCP (cine.CinemaClient)", Verificador::clienteCine);

        OUT.println();
        OUT.println("================== RESUMEN ==================");
        for (Map.Entry<String, int[]> e : porSeccion.entrySet()) {
            int[] v = e.getValue();
            OUT.printf("  %-44s %2d/%-2d%n", e.getKey(), v[0], v[0] + v[1]);
        }
        OUT.println("---------------------------------------------");
        OUT.printf("  TOTAL: %d/%d pruebas aprobadas%n", aprobadas, aprobadas + falladas);
        OUT.println("  (si una seccion se detuvo antes, su total es menor)");
        OUT.println("=============================================");
        System.exit(0);
    }

    interface Seccion { void correr() throws Exception; }

    private static void ejecutar(String titulo, Seccion s) {
        seccionActual = titulo;
        porSeccion.put(titulo, new int[2]);
        OUT.println();
        OUT.println("== " + titulo + " ==");
        try {
            s.correr();
        } catch (Throwable t) {
            Throwable c = (t instanceof InvocationTargetException && t.getCause() != null) ? t.getCause() : t;
            fallo("La seccion se detuvo por una excepcion: " + c);
        }
    }

    private static void check(String nombre, boolean ok, String pista) {
        if (ok) {
            aprobadas++;
            porSeccion.get(seccionActual)[0]++;
            OUT.println("  [OK]    " + nombre);
        } else {
            falladas++;
            porSeccion.get(seccionActual)[1]++;
            OUT.println("  [FALLA] " + nombre + (pista == null ? "" : "\n          -> " + pista));
        }
    }

    private static void fallo(String msg) {
        check(msg, false, null);
    }

    private static void igual(String nombre, String esperado, String obtenido) {
        check(nombre, esperado.equals(obtenido), "esperado \"" + esperado + "\" pero se obtuvo \"" + visible(obtenido) + "\"");
    }

    private static String visible(String s) {
        if (s == null) return "null";
        int nulos = 0;
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == 0) nulos++;
            else if (c < 32) sb.append('?');
            else sb.append(c);
        }
        return nulos == 0 ? sb.toString()
                : sb + "\" + " + nulos + " bytes basura (\\0): se convirtio todo el buffer en vez de usar getLength()";
    }

    private static String codigo(Runnable r) {
        try {
            r.run();
            return "OK";
        } catch (CinemaException e) {
            return e.getMessage();
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName();
        }
    }

    // ------------------------------------------------------------------ 1
    private static void logicaUdp() {
        ServicesImpl s = new ServicesImpl();
        UDPController u = new UDPController(s, 0);

        igual("PING", "PONG", u.process("PING"));
        igual("PING con campos extra", "ERROR;INVALID_FORMAT", u.process("PING;1"));
        igual("FREE con la sala vacia", "FREE;30", u.process("FREE"));
        s.reserve(0, 0, "ana");
        igual("FREE despues de una reserva", "FREE;29", u.process("FREE"));
        igual("SEAT de una silla reservada", "SEAT;0;0;TAKEN", u.process("SEAT;0;0"));
        igual("SEAT de una silla libre", "SEAT;4;5;FREE", u.process("SEAT;4;5"));
        igual("SEAT fila fuera de la sala", "ERROR;INVALID_SEAT", u.process("SEAT;5;0"));
        igual("SEAT columna negativa", "ERROR;INVALID_SEAT", u.process("SEAT;0;-1"));
        igual("SEAT con fila no numerica", "ERROR;INVALID_FORMAT", u.process("SEAT;a;0"));
        igual("SEAT con campos de menos", "ERROR;INVALID_FORMAT", u.process("SEAT;0"));
        igual("SEAT con campos de mas", "ERROR;INVALID_FORMAT", u.process("SEAT;0;0;x"));
        igual("Comando en minusculas", "ERROR;INVALID_FORMAT", u.process("free"));
        igual("Mensaje vacio", "ERROR;INVALID_FORMAT", u.process(""));
    }

    // ------------------------------------------------------------------ 2
    private static void servicios() throws Exception {
        ServicesImpl s = new ServicesImpl();
        igual("Reservar silla libre", "OK", codigo(() -> s.reserve(2, 2, "ana")));
        igual("Reservar silla ocupada", "SEAT_TAKEN", codigo(() -> s.reserve(2, 2, "bob")));
        igual("Reservar fuera de la sala", "INVALID_SEAT", codigo(() -> s.reserve(7, 0, "bob")));
        igual("Reservar sin usuario", "INVALID_DATA", codigo(() -> s.reserve(1, 1, " ")));
        igual("Cancelar silla de otro", "NOT_YOURS", codigo(() -> s.cancel(2, 2, "bob")));
        igual("Cancelar silla libre", "NOT_YOURS", codigo(() -> s.cancel(3, 3, "ana")));
        igual("Cancelar la propia", "OK", codigo(() -> s.cancel(2, 2, "ana")));
        check("Despues de cancelar la sala vuelve a tener 30 libres", s.freeCount() == 30, "freeCount = " + s.freeCount());

        int malas = 0;
        for (int ronda = 0; ronda < 20; ronda++) {
            ServicesImpl s2 = new ServicesImpl();
            if (carrera(30, i -> s2.reserve(2, 3, "u" + i)) != 1) malas++;
        }
        check("30 usuarios reservan la MISMA silla a la vez -> solo 1 gana (20 rondas)", malas == 0,
                malas + " de 20 rondas dejaron mas de una reserva exitosa: condicion de carrera");
    }

    interface Accion { void hacer(int i) throws Exception; }

    private static int carrera(int n, Accion a) throws InterruptedException {
        CountDownLatch salida = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger();
        List<Thread> hilos = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int k = i;
            Thread t = new Thread(() -> {
                try {
                    salida.await();
                    a.hacer(k);
                    exitos.incrementAndGet();
                } catch (Exception ignored) {
                }
            });
            t.setDaemon(true);
            hilos.add(t);
            t.start();
        }
        salida.countDown();
        for (Thread t : hilos) t.join(5000);
        return exitos.get();
    }

    // ------------------------------------------------------------------ 3
    private static void servidorUdp() throws Exception {
        int puerto = 5100;
        ServicesImpl s = new ServicesImpl();
        UDPController server = new UDPController(s, puerto);
        Thread t = new Thread(server::startService);
        t.setDaemon(true);
        t.start();
        Thread.sleep(400);
        try {
            DatagramSocket ds = campo(server, DatagramSocket.class);
            if (ds == null || !ds.isBound()) {
                fallo("UDPController no pudo abrir su DatagramSocket\n          -> revisa la direccion IP con la que se enlaza");
                return;
            }
            String r = udp(puerto, "PING", 1500);
            check("PING por la red -> PONG", "PONG".equals(r),
                    r == null ? "no llego respuesta"
                            : "se obtuvo \"" + visible(r) + "\". process(\"PING\") directo si funciona (seccion 1), "
                            + "asi que el problema esta en como se convierten a String los bytes recibidos");
            s.reserve(1, 2, "ana");
            igual("SEAT por la red", "SEAT;1;2;TAKEN", udp(puerto, "SEAT;1;2", 1500));
            udp(puerto, "SEAT;1;2XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX", 1500);
            igual("Mensaje corto despues de uno largo", "FREE;29", udp(puerto, "FREE", 1500));
            try (DatagramSocket a = new DatagramSocket(); DatagramSocket b = new DatagramSocket()) {
                a.setSoTimeout(1500);
                b.setSoTimeout(1500);
                enviar(a, puerto, "PING");
                enviar(b, puerto, "FREE");
                String ra = recibir(a), rb = recibir(b);
                check("Cada cliente recibe SU respuesta", "PONG".equals(ra) && "FREE;29".equals(rb),
                        "A recibio \"" + visible(ra) + "\", B recibio \"" + visible(rb) + "\"");
            }
        } finally {
            server.stop();
        }
    }

    private static String udp(int puerto, String msg, int timeout) throws IOException {
        try (DatagramSocket s = new DatagramSocket()) {
            s.setSoTimeout(timeout);
            enviar(s, puerto, msg);
            return recibir(s);
        }
    }

    private static void enviar(DatagramSocket s, int puerto, String msg) throws IOException {
        byte[] d = msg.getBytes(StandardCharsets.UTF_8);
        s.send(new DatagramPacket(d, d.length, InetAddress.getByName("localhost"), puerto));
    }

    private static String recibir(DatagramSocket s) throws IOException {
        byte[] buf = new byte[2048];
        DatagramPacket p = new DatagramPacket(buf, buf.length);
        try {
            s.receive(p);
        } catch (SocketTimeoutException e) {
            return null;
        }
        return new String(p.getData(), p.getOffset(), p.getLength(), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ 4
    private static String req(String action, String... kv) {
        JsonObject o = new JsonObject();
        o.addProperty("action", action);
        JsonObject d = new JsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) d.addProperty(kv[i], kv[i + 1]);
        o.add("data", d);
        return o.toString();
    }

    private static JsonObject tcp(int puerto, String linea) {
        return tcp(puerto, linea, null);
    }

    private static JsonObject tcp(int puerto, String linea, boolean[] cerro) {
        try (Socket s = new Socket("localhost", puerto)) {
            s.setSoTimeout(2000);
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            w.write(linea);
            w.newLine();
            w.flush();
            String resp = r.readLine();
            if (resp == null) return null;
            if (cerro != null) {
                try {
                    cerro[0] = r.readLine() == null;
                } catch (IOException e) {
                    cerro[0] = false;
                }
            }
            JsonElement e = JsonParser.parseString(resp);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean ok(JsonObject r) {
        return r != null && r.has("status") && !r.get("status").isJsonNull()
                && "OK".equals(r.get("status").getAsString()) && r.has("data");
    }

    private static String msg(JsonObject r) {
        if (r == null) return "(sin respuesta: el servidor no contesto ni cerro la conexion)";
        try {
            if (ok(r)) return "OK";
            return r.getAsJsonObject("data").get("message").getAsString();
        } catch (Exception e) {
            return r.toString();
        }
    }

    private static int libres(JsonObject r) {
        try {
            return r.getAsJsonObject("data").get("free").getAsInt();
        } catch (Exception e) {
            return -1;
        }
    }

    private static void servidorTcp() throws Exception {
        int puerto = 12445;
        ServicesImpl svc = new ServicesImpl();
        TCPController server = new TCPController(svc, puerto);
        if (campo(server, ServerSocket.class) == null) {
            fallo("TCPController no pudo crear el ServerSocket");
            return;
        }
        Thread t = new Thread(server::startService);
        t.setDaemon(true);
        t.start();
        Thread.sleep(300);

        JsonObject r = tcp(puerto, req("GET_SEATS"));
        boolean dims = false;
        try {
            dims = r.getAsJsonObject("data").getAsJsonArray("seats").size() == 5
                    && r.getAsJsonObject("data").getAsJsonArray("seats").get(0).getAsJsonArray().size() == 6;
        } catch (Exception ignored) {
        }
        check("GET_SEATS devuelve la sala 5x6 y free=30", ok(r) && dims && libres(r) == 30, "respuesta: " + r);

        r = tcp(puerto, req("RESERVE", "row", "1", "col", "1", "user", "ana"));
        String owner = null;
        try {
            owner = r.getAsJsonObject("data").getAsJsonArray("seats").get(1).getAsJsonArray().get(1)
                    .getAsJsonObject().get("owner").getAsString();
        } catch (Exception ignored) {
        }
        check("RESERVE (1,1) para ana", ok(r) && "ana".equals(owner) && libres(r) == 29, "respuesta: " + r);
        igual("RESERVE silla ocupada", "SEAT_TAKEN", msg(tcp(puerto, req("RESERVE", "row", "1", "col", "1", "user", "bob"))));
        igual("RESERVE fuera de la sala", "INVALID_SEAT", msg(tcp(puerto, req("RESERVE", "row", "9", "col", "9", "user", "bob"))));
        igual("RESERVE sin usuario", "INVALID_DATA", msg(tcp(puerto, req("RESERVE", "row", "0", "col", "0", "user", ""))));

        boolean[] cerro = new boolean[1];
        String m = msg(tcp(puerto, req("RESERVE", "row", "x", "col", "0", "user", "bob"), cerro));
        igual("RESERVE con fila no numerica", "INVALID_DATA", m);
        check("Despues de un error el servidor igual cierra la conexion", cerro[0],
                "la conexion queda abierta: el socket no se cierra cuando algo falla");
        igual("RESERVE sin data", "INVALID_DATA", msg(tcp(puerto, "{\"action\":\"RESERVE\"}")));

        igual("CANCEL de una silla ajena", "NOT_YOURS", msg(tcp(puerto, req("CANCEL", "row", "1", "col", "1", "user", "bob"))));
        r = tcp(puerto, req("CANCEL", "row", "1", "col", "1", "user", "ana"));
        check("CANCEL de la propia devuelve la sala con free=30", ok(r) && libres(r) == 30, "respuesta: " + r);
        igual("CANCEL con columna no numerica", "INVALID_DATA", msg(tcp(puerto, req("CANCEL", "row", "1", "col", "?", "user", "ana"))));
        igual("Accion desconocida", "UNKNOWN_ACTION", msg(tcp(puerto, req("VOLAR"))));
        igual("Linea que no es JSON", "INVALID_JSON", msg(tcp(puerto, "esto no es json")));

        AtomicInteger oks = new AtomicInteger();
        CountDownLatch salida = new CountDownLatch(1);
        List<Thread> hilos = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int k = i;
            Thread h = new Thread(() -> {
                try {
                    salida.await();
                } catch (InterruptedException ignored) {
                }
                if (ok(tcp(puerto, req("RESERVE", "row", "3", "col", "3", "user", "c" + k)))) oks.incrementAndGet();
            });
            h.setDaemon(true);
            hilos.add(h);
            h.start();
        }
        salida.countDown();
        for (Thread h : hilos) h.join(8000);
        check("20 clientes TCP reservan la (3,3) a la vez -> solo 1 OK", oks.get() == 1,
                oks.get() + " clientes recibieron OK por la misma silla");
    }

    @SuppressWarnings("unchecked")
    private static <T> T campo(Object o, Class<T> tipo) {
        try {
            for (Field f : o.getClass().getDeclaredFields()) {
                if (tipo.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return (T) f.get(o);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------------ 5
    private static void arranque() throws Exception {
        if (!puertosLibres()) {
            fallo("Los puertos 12345/5000 estan ocupados: cierra tu servidor y vuelve a verificar");
            return;
        }
        final Throwable[] error = new Throwable[1];
        Thread t = new Thread(() -> {
            try {
                co.icesi.cine.Main.main(new String[0]);
            } catch (Throwable e) {
                error[0] = e;
            }
        });
        t.setDaemon(true);
        t.start();
        Thread.sleep(1500);
        if (error[0] != null) {
            fallo("Main lanzo " + error[0]);
            return;
        }
        String r = udp(5000, "PING", 1500);
        check("Main levanta el UDP en 5000", "PONG".equals(r), "PING a 5000 obtuvo: " + visible(r));
        JsonObject j = tcp(12345, req("GET_SEATS"));
        check("Main levanta el TCP en 12345 AL MISMO TIEMPO", ok(j),
                j == null ? "nadie responde en 12345: el arranque de un servidor bloquea al otro" : "respuesta: " + j);
        if (ok(j) && "PONG".equals(r)) {
            tcp(12345, req("RESERVE", "row", "4", "col", "0", "user", "zoe"));
            igual("TCP y UDP comparten el estado", "SEAT;4;0;TAKEN", udp(5000, "SEAT;4;0", 1500));
        }
    }

    private static boolean puertosLibres() {
        try (ServerSocket a = new ServerSocket(12345); DatagramSocket b = new DatagramSocket(5000)) {
            return a.isBound() && b.isBound();
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 6
    private static void clienteKiosco() throws Exception {
        Class<?> cls;
        try {
            cls = Class.forName("kiosco.KioskClient");
        } catch (ClassNotFoundException e) {
            fallo("No existe la clase kiosco.KioskClient");
            return;
        }
        Constructor<?> ctor;
        Method query;
        try {
            ctor = cls.getConstructor(String.class, int.class, int.class);
            query = cls.getMethod("query", String.class);
        } catch (NoSuchMethodException e) {
            fallo("Firma incorrecta: KioskClient(String host, int port, int timeoutMs) y String query(String)");
            return;
        }
        check("Existe kiosco.KioskMain con main()", tieneMain("kiosco.KioskMain"), null);

        DatagramSocket eco = new DatagramSocket(5200);
        Thread t = new Thread(() -> {
            byte[] buf = new byte[2048];
            while (!eco.isClosed()) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    eco.receive(p);
                    String m = new String(p.getData(), p.getOffset(), p.getLength(), StandardCharsets.UTF_8);
                    byte[] out = ("ECO:" + m).getBytes(StandardCharsets.UTF_8);
                    eco.send(new DatagramPacket(out, out.length, p.getAddress(), p.getPort()));
                } catch (IOException ignored) {
                }
            }
        });
        t.setDaemon(true);
        t.start();
        try {
            Object c = ctor.newInstance("localhost", 5200, 1000);
            igual("Envia y recibe", "ECO:SEAT;1;1", (String) query.invoke(c, "SEAT;1;1"));
            query.invoke(c, "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
            igual("Respuesta corta despues de una larga", "ECO:FREE", (String) query.invoke(c, "FREE"));
        } finally {
            eco.close();
        }

        try (DatagramSocket mudo = new DatagramSocket(5201)) {
            Object c = ctor.newInstance("localhost", 5201, 400);
            long t0 = System.currentTimeMillis();
            Throwable lanzada = null;
            try {
                query.invoke(c, "PING");
            } catch (InvocationTargetException e) {
                lanzada = e.getCause();
            }
            long dt = System.currentTimeMillis() - t0;
            check("Sin respuesta lanza SocketTimeoutException segun el timeout", lanzada instanceof SocketTimeoutException
                    && dt < 2500 && mudo.isBound(), "lanzo " + lanzada + " en " + dt + " ms");
        }
    }

    // ------------------------------------------------------------------ 7
    private static void clienteCine() throws Exception {
        Class<?> reqCls, cliCls;
        try {
            reqCls = Class.forName("cine.Request");
            Class.forName("cine.Response");
            cliCls = Class.forName("cine.CinemaClient");
        } catch (ClassNotFoundException e) {
            fallo("No existe " + e.getMessage() + " (se necesitan cine.Request, cine.Response y cine.CinemaClient)");
            return;
        }
        Constructor<?> ctor;
        Method send;
        try {
            ctor = cliCls.getConstructor(String.class, int.class);
            send = cliCls.getMethod("sendRequest", reqCls);
        } catch (NoSuchMethodException e) {
            fallo("Firma incorrecta: CinemaClient(String host, int port) y Response sendRequest(Request)");
            return;
        }
        check("Existe cine.ClientMain con main()", tieneMain("cine.ClientMain"), null);

        String[] respuestas = {
                "{\"status\":\"OK\",\"data\":{\"free\":29.0,\"seats\":[[{\"reserved\":true,\"owner\":\"ana\"},{\"reserved\":false}]]}}",
                "{\"status\":\"ERROR\",\"data\":{\"message\":\"SEAT_TAKEN\"}}"
        };
        List<String> lineas = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger conexiones = new AtomicInteger();
        ServerSocket falso = new ServerSocket(12545);
        Thread t = new Thread(() -> {
            while (!falso.isClosed()) {
                try (Socket s = falso.accept()) {
                    int n = conexiones.getAndIncrement();
                    s.setSoTimeout(2000);
                    BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    String l;
                    try {
                        l = r.readLine();
                    } catch (SocketTimeoutException e) {
                        l = null;
                    }
                    lineas.add(l);
                    if (l == null) continue;
                    BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
                    w.write(respuestas[Math.min(n, 1)]);
                    w.newLine();
                    w.flush();
                } catch (IOException ignored) {
                }
            }
        });
        t.setDaemon(true);
        t.start();

        try {
            Object cliente = ctor.newInstance("localhost", 12545);
            Object rq = nuevoRequest(reqCls, "RESERVE");
            datos(rq).put("row", "0");
            datos(rq).put("col", "0");
            datos(rq).put("user", "ana");
            Object r1 = conLimite(() -> send.invoke(cliente, rq), 4000);

            String l0 = lineas.isEmpty() ? null : lineas.get(0);
            check("Envia el JSON en una linea terminada en \\n (con flush)", l0 != null,
                    "el servidor no recibio una linea completa: falta newLine()/println o flush()");
            if (l0 != null) {
                JsonObject j = null;
                try {
                    j = JsonParser.parseString(l0).getAsJsonObject();
                } catch (Exception ignored) {
                }
                check("La peticion es JSON con action y data", j != null && "RESERVE".equals(j.get("action").getAsString())
                        && "ana".equals(j.getAsJsonObject("data").get("user").getAsString())
                        && "0".equals(j.getAsJsonObject("data").get("row").getAsString()), "se recibio: " + l0);
            }
            check("Deserializa la respuesta (status OK, data con seats)", r1 != null && "OK".equals(campoPublico(r1, "status"))
                    && campoPublico(r1, "data") instanceof Map && ((Map<?, ?>) campoPublico(r1, "data")).containsKey("seats"),
                    "se obtuvo: " + describir(r1));

            Object r2 = conLimite(() -> send.invoke(cliente, nuevoRequest(reqCls, "GET_SEATS")), 4000);
            check("Segunda peticion: conexion nueva y lee el ERROR",
                    r2 != null && "ERROR".equals(campoPublico(r2, "status")) && conexiones.get() == 2,
                    "conexiones abiertas: " + conexiones.get() + ", respuesta: " + describir(r2));
        } finally {
            falso.close();
        }
    }

    private static Object conLimite(Callable<Object> c, long ms) {
        final Object[] res = new Object[1];
        Thread t = new Thread(() -> {
            try {
                res[0] = c.call();
            } catch (Exception ignored) {
            }
        });
        t.setDaemon(true);
        t.start();
        try {
            t.join(ms);
        } catch (InterruptedException ignored) {
        }
        return res[0];
    }

    private static Object nuevoRequest(Class<?> cls, String action) throws Exception {
        Object o;
        try {
            o = cls.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            o = cls.getDeclaredConstructor(String.class).newInstance(action);
        }
        cls.getField("action").set(o, action);
        if (cls.getField("data").get(o) == null) cls.getField("data").set(o, new HashMap<String, String>());
        return o;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> datos(Object rq) throws Exception {
        return (Map<String, String>) rq.getClass().getField("data").get(rq);
    }

    private static Object campoPublico(Object o, String nombre) {
        try {
            return o.getClass().getField(nombre).get(o);
        } catch (Exception e) {
            return null;
        }
    }

    private static String describir(Object resp) {
        if (resp == null) return "null (no respondio, se colgo o lanzo excepcion)";
        return "status=" + campoPublico(resp, "status") + ", data=" + campoPublico(resp, "data");
    }

    private static boolean tieneMain(String clase) {
        try {
            Method m = Class.forName(clase).getMethod("main", String[].class);
            return java.lang.reflect.Modifier.isStatic(m.getModifiers());
        } catch (Exception e) {
            return false;
        }
    }
}

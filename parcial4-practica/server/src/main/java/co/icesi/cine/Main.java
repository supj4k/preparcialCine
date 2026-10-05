package co.icesi.cine;

import co.icesi.cine.controllers.TCPController;
import co.icesi.cine.controllers.UDPController;
import co.icesi.cine.services.ServicesImpl;

public class Main {

    public static void main(String[] args) {
        ServicesImpl serv = new ServicesImpl();

        UDPController udp = new UDPController(serv, 5000);
        udp.startService();

        TCPController tcp = new TCPController(serv);
        tcp.startService();
    }
}

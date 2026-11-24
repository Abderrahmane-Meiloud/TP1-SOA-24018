package mr.supnum.bib;

import jakarta.xml.ws.Endpoint;
import java.util.concurrent.Executors;

public class Serveur {
    public static void main(String[] args) {
        // mvn exec:java exécute main() sur un thread dont le contexte voit bien jaxb-runtime
        // et saaj-impl, mais le pool HTTP interne de com.sun.net.httpserver crée ses propres
        // threads sans ce contexte : les Faults (JAXB, SAAJ) ne peuvent alors plus se sérialiser.
        // On fournit donc à l'Endpoint un Executor dont chaque thread hérite explicitement du
        // classloader qui a chargé cette classe (celui qui voit toutes les dépendances Maven).
        ClassLoader cl = Serveur.class.getClassLoader();
        Endpoint endpoint = Endpoint.create(new EmpruntService());
        endpoint.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setContextClassLoader(cl);
            return t;
        }));
        String url = "http://localhost:8080/bib/EmpruntService";
        endpoint.publish(url);
        System.out.println("WSDL : " + url + "?wsdl");
    }
}

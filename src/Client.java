import java.io.*;
import java.net.*;
public class Client {
    private Socket client;
    private PrintWriter output;
    private BufferedReader input;

    public void startConnection(String ip, int port) {
        try {
            client = new Socket(ip, port);
            output = new PrintWriter(client.getOutputStream(), true);
            input = new BufferedReader(new InputStreamReader(client.getInputStream()));

            output.println("HELO");
            System.out.println("SENT HELO");

            String res = input.readLine();
            System.out.println("RCVD " + res);

            if ("G'DAY".equals(res)) {
                output.println("BYE");
                System.out.println("SENT BYE");
            }

            // Reading BYE from server
            res = input.readLine();
            System.out.println("RCVD " + res);

            if ("BYE".equals(res)) {
                System.out.println("Client quitting...");
            }
        } catch (UnknownHostException ex) {
            System.out.println("Server not found: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("I/O error " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        Client client = new Client();
        client.startConnection("localhost", 4718);
    }
}
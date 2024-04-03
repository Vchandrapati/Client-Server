import java.io.*;
import java.net.*;
public class Server {
    private ServerSocket ss;
    private Socket client;
    private PrintWriter output;
    private BufferedReader input;

    public void start(int port) throws IOException {
        ss = new ServerSocket(port);
        System.out.println("Server listing on port " + port);
        client = ss.accept();
        System.out.println("Client Connected");

        output = new PrintWriter(client.getOutputStream(), true);
        input = new BufferedReader(new InputStreamReader(client.getInputStream()));

        String msg = input.readLine();
        System.out.println("RCVD " + msg);

        if ("HELO".equals(msg)) {
            output.println("G'DAY");
            System.out.println("SENT G'DAY");
        }

        msg = input.readLine();
        System.out.println("RCVD " + msg);

        if ("BYE".equals(msg)) {
            output.println("BYE");
            System.out.println("SENT BYE\nServer quitting...");
        }
    }

    public static void main(String[] args) throws IOException {
        Server server =new Server();
        server.start(4718);
    }
}
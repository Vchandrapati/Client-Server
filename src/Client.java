import java.io.*;
import java.net.*;
import java.util.*;

public class Client {

    private PrintWriter output;
    private BufferedReader input;
    private int serverCount;
    private TreeSet<Server> serverList;
    private ArrayList<Job> jobList;
    private ArrayList<Job> queuedJobs;
    String response;
    public static final int SEARCH_AREA = 10;

    public Client() {
        Comparator<Server> loadComparator = Comparator
                .comparing((Server s) -> !s.isActive)
                .thenComparingInt(s -> s.currentCores)
                .thenComparingInt(s -> s.currentMemorySize)
                .thenComparingInt(s -> s.currentDiskSize)
                .thenComparingInt(s -> s.id);

        serverList = new TreeSet<>(loadComparator); // Used pq for efficiency and sorting
        jobList = new ArrayList<>(); // Keep track of jobs for allocation
        queuedJobs = new ArrayList<>(); // Keep track of jobs in global queue
    }

    public void startConnection(String ip, int port) {
        try (Socket clientSocket = new Socket(ip, port)) {
            output = new PrintWriter(clientSocket.getOutputStream(), true);
            input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            sendCommand("HELO"); // Initiate connection with server
            readResponse();

            sendCommand("AUTH user");
            readResponse();

            sendCommand("GETS All"); // Ask server for server list
            String dataResponse = input.readLine();
            System.out.println("RCVD " + dataResponse);
            serverCount = Integer.parseInt(dataResponse.split(" ")[1]); // Get number of servers that return

            sendCommand("OK"); // Get list of servers and their details
            getAllServers(); // Get local copy of all server to operate on

            sendCommand("OK"); // Indicate ready for jobs
            readResponse();

            handleJobs();
        } catch (UnknownHostException ex) {
            System.out.println("Server not found: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("I/O error " + ex.getMessage());
        }
    }

    private void sendCommand(String command) {
        output.print(command + '\n'); // Server expects newline delimiter
        output.flush(); // Send message immediately
        System.out.println("SENT " + command);
    }

    private void readResponse() throws IOException {
        response = input.readLine();
        System.out.println("RCVD " + response);
    }

    /**
     * Utilises GETS All to get every server that is available and sorts them in a TreeSet to be accessed and tracked
     * more efficiently
     */
    private void getAllServers() throws IOException {
        if(!serverList.isEmpty()) serverList.clear();
        for (int i = 0; i < serverCount; i++) {
            String server = input.readLine().trim(); // Remove any leading or trailing whitespaces
            String[] serverDetails = server.split(" "); // Seperate string to find server details
            int id = Integer.parseInt(serverDetails[1]);
            int coreCount = Integer.parseInt(serverDetails[4]);
            int memorySize = Integer.parseInt(serverDetails[5]);
            int diskSize = Integer.parseInt(serverDetails[6]);

            serverList.add(new Server(serverDetails[0], id, coreCount, memorySize, diskSize)); // Add server to local list
        }
    }

    /**
     * Compares the jobs core, memory and disk requirements to find the minimum possible server that can process the job
     *
     * @param job the job to be assigned a server
     * @return The first server that can handle the job
     */
    private Server findCapableServer(Job job) {
        for (Server server : serverList) {
            if (server.maxCores >= job.coreReq && server.maxMemorySize >= job.memoryReq && server.maxDiskSize >= job.diskReq) {
                return server;
            }
        }
        return null;
    }

    /**
     * Compares the jobs core, memory and disk requirements with a servers current available resources to find the
     * minimum possible server that can process the job
     *
     * @param job the job to be assigned a server
     * @return The first server that can handle the job that has resources available such tha the job doesn't get queued
     */
    private Server findLeastLoaded(Job job) {
        for (Server server : serverList) {
            if (server.canHandleJob(job)) {
                return server;
            }
        }
        return null;
    }

    /**
     * Handles all jobs that are sent by the server and subsequent related messages including ending the transmission after
     */
    private void handleJobs() throws IOException {
        while (true) {
            sendCommand("REDY"); // Signal to server that you are ready to receive jobs
            response = input.readLine();
            System.out.println("RCVD " + response);

            if (response.startsWith("JOBN") || response.startsWith("JOBP")) { // Continue as long as server sends jobs
                Job job = getJob(response); // Get job details
                Server server = findLeastLoaded(job); // Utilise an algorithm to get the best server for a job

                if(response.startsWith("JOBP")) { // If it is a recalled job remove it from the local queue
                    queuedJobs.removeIf(qjob -> qjob.jobID == job.jobID);
                    if(server == null) server = findCapableServer(job);// Ignore load balancing since all servers will be full at this point
                }

                if(server == null) {
                    queuedJobs.add(job); // If a server cant be found for the job, queue it to schedule it later
                    sendCommand("ENQJ GQ"); // Put it on global queue
                    readResponse();
                } else {
                    allocateJob(server, job);
                    readResponse();

                    if (!response.equals("OK")) { // If response is not OK error present
                        System.out.println("Error scheduling job, received: " + response);
                        break; // Stop if scheduling failed
                    }
                }
            } else if (response.startsWith("JCPL")) {
                handleJobCompletion(response);
            } else if(response.startsWith("CHKQ")) {
                cleanGlobalQueue();
            } else if (response.equals("NONE") || response.equals(".")) { // Server will send NONE or . if no more jobs exist
                System.out.println("No more jobs to schedule");
                sendCommand("QUIT");
                readResponse();
                System.exit(0); // Close client if there are no more jobs to schedule
            }
        }
    }


    /**
     * When a job is completed, updates related servers resource allocations and checks globla queuee to see if any job
     * can be assigned in its place to reduce turnaround times
     * @param command Response from server
     */
    private void handleJobCompletion(String command) throws IOException {
        Server completedServer;
        int jobID = Integer.parseInt(command.split(" ")[2]); // Get jobID of completed job

        Job completedJob = jobList.stream()
                .filter(job -> job.jobID == jobID) // Find job in job list
                .findFirst()
                .orElse(null);

        if(completedJob != null) {
            completedServer = completedJob.assignedServer; // Get the server that was assigned to the job
            deAllocateJob(completedServer, completedJob);
            Job newJob = null;
            int searchArea = SEARCH_AREA; // Search first 10 jobs as it is to inefficient to search hundreds of jobs at once

            outer: if(!queuedJobs.isEmpty()) {
                while (newJob == null) { // Until a job is found keep increasing search radius and search again
                    newJob = findBestJobForServer(completedServer, searchArea);
                    if(queuedJobs.size() < searchArea) // Ensure you do not exceed array size
                        break outer;
                    searchArea += 10; // Gradually increase radius if no jobs found
                }

                int qPos = queuedJobs.indexOf(newJob) - 1;
                sendCommand("DEQJ GQ " + qPos); // Receive first job
                readResponse();
                sendCommand("REDY");
                readResponse();

                queuedJobs.remove(newJob); // Remove job from queue
                allocateJob(completedServer, newJob);
            }

            terminateIdleServer(completedServer); // Check if server can be terminated for cost saving
        } else {
            System.out.println("Error finding job: " + jobID);
        }
    }

    private Job findBestJobForServer(Server server, int searchArea) {
        Job bestJob = null;
        double bestUtilisation = 0;

        for (int i = searchArea - SEARCH_AREA; i < searchArea; i++) { // Ensure you don't check areas you already checked
            if(i < queuedJobs.size())
                break;

            Job job = queuedJobs.get(i);
            if (server.canHandleJob(job)) {
                double utilisation = calculateJobUtilisation(job, server); // Compares how well each job can utilise the resources available from the server
                if (utilisation < bestUtilisation) {
                    bestUtilisation = utilisation;
                    bestJob = job;
                }
            }
        }

        return bestJob;
    }

    private double calculateJobUtilisation(Job job, Server server) {
        double coreFitness = (double) job.coreReq / server.maxCores;
        double memoryFitness = (double) job.memoryReq / server.maxMemorySize;
        double diskFitness = (double) job.diskReq / server.maxDiskSize;
        return (coreFitness + memoryFitness + diskFitness) / 3; // Average of 3 scores is used to determine how efficient a job wil fill the slot
    }

    private void cleanGlobalQueue() throws IOException {
        if(!queuedJobs.isEmpty()) {
            int qPos = queuedJobs.size() - 1;
            sendCommand("DEQJ GQ " + qPos);
            response = input.readLine();
            handleJobs();
        }
    }

    /**
     * Checks if a server is active and terminates it to save resources and cost if it isn't
     * @param server The server you are attempting to terminate
     */
    private void terminateIdleServer(Server server) throws IOException {
        if (!server.isActive) {
            sendCommand("TERM " + server.type + " " + server.id);
            readResponse();
        }
    }

    private void allocateJob(Server server, Job job) {
        serverList.remove(server); // Remove to update and reinsert to maintain list order
        server.allocateResources(job);
        sendCommand("SCHD " + job.jobID + " " + server.type + " " + server.id); // Schedule job
        job.assignServer(server); // Assign server to job for tracking
        serverList.add(server);
        jobList.add(job); // Keep track of job for deallocation
    }

    private void deAllocateJob(Server server, Job job) {
        serverList.remove(server); // // Remove to update and reinsert to maintain list order
        server.deAllocateResources(job);
        serverList.add(server);
        jobList.remove(job); // Delete job to reduce overheads
    }

    private Job getJob(String data) {
        String[] parts = data.trim().split(" ");
        int jobID = Integer.parseInt(parts[1]);
        long submitTime = Long.parseLong(parts[2]);
        long estRuntime = Long.parseLong(parts[6]);
        int coreReq = Integer.parseInt(parts[3]);
        int memoryReq = Integer.parseInt(parts[4]);
        int diskReq = Integer.parseInt(parts[5]);
        return new Job(jobID, estRuntime, coreReq, memoryReq, diskReq, submitTime);
    }

    public static void main(String[] args) {
        Client client = new Client();
        client.startConnection("localhost", 54718);
    }
}
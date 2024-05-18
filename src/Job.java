public class Job {
    int jobID, coreReq, memoryReq, diskReq;
    long estRuntime, submitTime;
    Server assignedServer;

    Job(int jobID, long estRuntime, int coreReq, int memoryReq, int diskReq, long submitTime) {
        this.jobID = jobID;
        this.estRuntime = estRuntime;
        this.coreReq = coreReq;
        this.memoryReq = memoryReq;
        this.diskReq = diskReq;
        this.submitTime = submitTime;
    }

    void printJobDetails() {
        System.out.println(jobID + " " + coreReq + " " + memoryReq + " " + diskReq);
    }

    void assignServer(Server server) {
        assignedServer = server;
    }
}

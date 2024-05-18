public class Server {
    String type;
    int id, maxCores, maxMemorySize, maxDiskSize, currentCores, currentMemorySize, currentDiskSize;
    boolean isActive;

    Server(String type, int id, int cores, int memorySize, int diskSize) {
        this.type = type;
        this.maxCores = cores;
        this.id = id;
        this.maxMemorySize = memorySize;
        this.maxDiskSize = diskSize;
        this.currentCores = cores;
        this.currentMemorySize = memorySize;
        this.currentDiskSize = diskSize;
        this.isActive = false;
    }

    void printServerDetails() {
        System.out.println(type + " " + id + " " + maxCores + " " + maxMemorySize + " " + maxDiskSize);
    }

    void allocateResources(Job job) {
        this.currentCores -= job.coreReq;
        this.currentMemorySize -= job.memoryReq;
        this.currentDiskSize -= job.diskReq;
        isActive = true;
    }

    void deAllocateResources(Job job) {
        this.currentCores += job.coreReq;
        this.currentMemorySize += job.memoryReq;
        this.currentDiskSize += job.diskReq;
        isActive = currentCores < maxCores || currentMemorySize < maxMemorySize || currentDiskSize < maxDiskSize;;
    }

    public boolean canHandleJob(Job job) {
        return job.coreReq <= currentCores && job.memoryReq <= currentMemorySize && job.diskReq <= currentDiskSize;
    }
}
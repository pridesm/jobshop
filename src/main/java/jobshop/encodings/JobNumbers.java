package jobshop.encodings;

import jobshop.Encoding;
import jobshop.Instance;
import jobshop.Schedule;

import java.util.Arrays;
import java.util.Queue;

/** Représentation par numéro de job. */
public class JobNumbers extends Encoding {

    /** A numJobs * numTasks array containing the representation by job numbers. */
    public final int[] jobs;

    /** In case the encoding is only partially filled, indicates the index of first
     * element of `jobs` that has not been set yet. */
    public int nextToSet = 0;

    public JobNumbers(Instance instance) {
        super(instance);
        jobs = new int[instance.numJobs * instance.numMachines];
        Arrays.fill(jobs, -1);
    }

    public JobNumbers(ResourceOrder enc) {
        super(enc.instance);
        jobs = new int[instance.numJobs * instance.numMachines];
        Queue<Schedule.ScheduledTask> queue = enc.toSchedule().orderedTaskQueue();
        Schedule.ScheduledTask next; int index = 0;
        while((next = queue.poll()) != null) {
            jobs[index++] = next.job;
        }
    }

    @Override
    public Schedule toSchedule() {
        // time at which each machine is going to be freed
        int[] nextFreeTimeResource = new int[instance.numMachines];

        // for each job, the first task that has not yet been scheduled
        int[] nextTask = new int[instance.numJobs];

        // for each task, its start time
        int[][] startTimes = new int[instance.numJobs][instance.numTasks];

        // compute the earliest start time for every task of every job
        for(int job : jobs) {
            int task = nextTask[job];
            int machine = instance.machine(job, task);
            // earliest start time for this task
            int est = task == 0 ? 0 : startTimes[job][task-1] + instance.duration(job, task-1);
            est = Math.max(est, nextFreeTimeResource[machine]);

            startTimes[job][task] = est;
            nextFreeTimeResource[machine] = est + instance.duration(job, task);
            nextTask[job] = task + 1;
        }

        return new Schedule(instance, startTimes);
    }

    @Override
    public String toString() {
        return Arrays.toString(Arrays.copyOfRange(jobs,0, nextToSet));
    }
}

package jobshop.encodings;

import jobshop.Encoding;
import jobshop.Instance;
import jobshop.Schedule;

import java.util.PriorityQueue;
import java.util.Queue;

public class ResourceOrder extends Encoding {

    public final Task[][] tasks;

    public ResourceOrder(Instance instance) {
        super(instance);
        tasks = new Task[instance.numMachines][instance.numJobs];
    }

    public ResourceOrder(JobNumbers enc) {
        super(enc.instance);
        tasks = new Task[instance.numMachines][instance.numJobs];
        Queue<Schedule.ScheduledTask> queue = enc.toSchedule().orderedTaskQueue();
        Schedule.ScheduledTask next; int[] indices = new int[instance.numMachines];
        while((next = queue.poll()) != null) {
            tasks[next.machine][indices[next.machine]++] = new Task(next.job, next.task);
        }
    }

    @Override
    public Schedule toSchedule() {
        int time = 0;
        int[][] startTimes = new int[instance.numJobs][instance.numTasks];

        // for each job, the first task that has not yet been scheduled
        int[] jobProgression = new int[instance.numJobs];

        // for each machine, the number of tasks executed
        int[] machineProgression = new int[instance.numMachines];

        // for each machine, true if processing a task
        boolean[] machineBusy = new boolean[instance.numMachines];

        class TaskInProgress implements Comparable<TaskInProgress> {
           final int endTime, job, machine;
            TaskInProgress(int endTime, int job, int machine) {
                this.endTime = endTime; this.job = job; this.machine = machine;
            }
            @Override
            public int compareTo(TaskInProgress o) {
                return Integer.compare(endTime, o.endTime);
            }
        }

        Queue<TaskInProgress> queue = new PriorityQueue<>();

        while (true) {
            for(int m = 0; m < instance.numMachines; m++) {
                if(machineProgression[m] >= tasks[0].length)
                    continue;
                Task nextTask = tasks[m][machineProgression[m]];
                if(!machineBusy[m] && nextTask.task == jobProgression[nextTask.job]) {
                    int duration = instance.duration(nextTask.job, nextTask.task);
                    queue.offer(new TaskInProgress(time + duration, nextTask.job, m));
                    startTimes[nextTask.job][nextTask.task] = time;
                    machineBusy[m] = true;
                }
            }
            TaskInProgress task = queue.poll();
            if(task == null)
                break;
            time = task.endTime;
            machineProgression[task.machine]++;
            jobProgression[task.job]++;
            machineBusy[task.machine] = false;
        }

        return new Schedule(instance, startTimes);
    }

    public ResourceOrder clone() {
        ResourceOrder newOrder = new ResourceOrder(instance);
        for(int i = 0; i < tasks.length; i++) {
            System.arraycopy(tasks[i], 0, newOrder.tasks[i], 0, tasks[i].length);
        }
        return newOrder;
    }
}

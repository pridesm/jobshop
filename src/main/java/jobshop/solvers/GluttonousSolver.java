package jobshop.solvers;

import jobshop.Instance;
import jobshop.Result;
import jobshop.Solver;
import jobshop.encodings.ResourceOrder;
import jobshop.encodings.Task;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;

public class GluttonousSolver implements Solver {

    public static final int PRIORITY_SPT = 0;
    public static final int PRIORITY_LPT = 1;
    public static final int PRIORITY_SRPT = 2;
    public static final int PRIORITY_LRPT = 3;

    public final int priorityMode;
    public ResourceOrder sol;

    public GluttonousSolver(int priorityMode) {
        this.priorityMode = priorityMode;
    }

    @Override
    public Result solve(Instance instance, long deadline) {
        sol = new ResourceOrder(instance);
        int time = 0;
        int[][] startTimes = new int[instance.numJobs][instance.numTasks];

        // for each job, the first task that has not yet been scheduled
        int[] jobProgression = new int[instance.numJobs];

        // for each machine, the number of tasks executed
        int[] machineProgression = new int[instance.numMachines];

        // for each machine, true if processing a task
        boolean[] machineBusy = new boolean[instance.numMachines];

        class TaskInfo {
            int endTime, job, task, machine, duration, remainingDuration;
        }
        Queue<TaskInfo> runningTasks = new PriorityQueue<>(new Comparator<TaskInfo>() {
            @Override
            public int compare(TaskInfo o1, TaskInfo o2) {
                return Integer.compare(o1.endTime, o2.endTime);
            }
        });
        Comparator<TaskInfo> comp = new Comparator<TaskInfo>() {
            @Override
            public int compare(TaskInfo o1, TaskInfo o2) {
                switch(priorityMode) {
                    case PRIORITY_SPT:
                        return Integer.compare(o1.duration, o2.duration);
                    case PRIORITY_LPT:
                        return -Integer.compare(o1.duration, o2.duration);
                    case PRIORITY_SRPT:
                        return Integer.compare(o1.remainingDuration, o2.remainingDuration);
                    case PRIORITY_LRPT:
                        return -Integer.compare(o1.remainingDuration, o2.remainingDuration);

                }
                return 0;
            }
        };
        Queue<TaskInfo>[] candidateQueue = new Queue[] {new PriorityQueue(comp), new PriorityQueue(comp)};

        int[] remainingDurations = new int[instance.numJobs];
        if(priorityMode == PRIORITY_SRPT || priorityMode == PRIORITY_LRPT) {
            for (int j = 0; j < instance.numJobs; j++) {
                for (int i = 0; i < instance.numTasks; i++) {
                    remainingDurations[j] += instance.duration(j, i);
                }
            }
        }
        TaskInfo[] finishingTasks = new TaskInfo[instance.numMachines];
        TaskInfo[][] pendingTasks = new TaskInfo[instance.numMachines][instance.numJobs];
        int[] pendingTasksIndices = new int[instance.numMachines];
        int finishingTaskIndex = 0;

        // ajout des premières tâches de chaque job à la queue des tâches candidates
        for(int j = 0; j < instance.numJobs; j++) {
            TaskInfo task = new TaskInfo();
            task.machine = instance.machine(j, 0);
            task.job = j;
            task.task = 0;
            task.duration = instance.duration(j, 0);
            task.remainingDuration = remainingDurations[task.machine];
            candidateQueue[0].offer(task);
        }

        // le sujet à changé sauf que j'avais déja fait tout ça donc je garde comme ça

        while (true) {
            // lancement des tâches par priorité
            TaskInfo task;
            while((task = candidateQueue[0].poll()) != null) {
                if(!machineBusy[task.machine]) {
                    machineBusy[task.machine] = true;
                    task.endTime = time + task.duration;
                    runningTasks.offer(task);
                    sol.tasks[task.machine][machineProgression[task.machine]++] = new Task(task.job, task.task);
                } else {
                    // cette tâche était candidate mais une tâche plus prioritaire lui est passée devant
                    // donc on la met candidate pour la prochaine phase (on aurait pu la laisser dans la même structure si on avait
                    // utilisé un arbre binaire pour trier les prioritées, mais la priority queue est implémentée avec un binary
                    // heap qui est généralement plus rapide donc j'ai fait le choix d'utiliser 2 binary heap
                    candidateQueue[1].offer(task);
                }
            }

            // swap des candidate queues
            Queue<TaskInfo> old = candidateQueue[0];
            candidateQueue[0] = candidateQueue[1];
            candidateQueue[1] = old;

            if(runningTasks.isEmpty())
                break;

            // libération des machines qui vienent de finir leur tâche
            time = runningTasks.peek().endTime;
            do {
                task = runningTasks.poll();
                machineBusy[task.machine] = false;
                while(pendingTasksIndices[task.machine] > 0) {
                    // toutes les tâches qui étaient en attente sur cette machine sont maintenant candidates
                    TaskInfo pendingTask = pendingTasks[task.machine][--pendingTasksIndices[task.machine]];
                    candidateQueue[0].offer(pendingTask);
                }
                remainingDurations[task.machine] -= task.duration;
                jobProgression[task.job]++;
                finishingTasks[finishingTaskIndex++] = task;
            } while((task = runningTasks.peek()) != null && task.endTime == time);

            // ajout des tâches candidates (on passe par un buffer car si on le faisait directement on ne respecterait pas l'heuristique)
            // (c'est plus efficace de regarder seulement les tâches suivantes dans le job plûtot que de tout regarder)
            while(finishingTaskIndex > 0) {
                task = finishingTasks[--finishingTaskIndex];
                if(task.task < instance.numTasks - 1) {
                    int m = instance.machine(task.job, task.task + 1);
                    TaskInfo newTask = new TaskInfo();
                    newTask.machine = m;
                    newTask.job = task.job;
                    newTask.task = task.task + 1;
                    newTask.duration = instance.duration(newTask.job, newTask.task);
                    newTask.remainingDuration = remainingDurations[newTask.machine];
                    if(!machineBusy[m]) {
                        candidateQueue[0].offer(newTask);
                    } else {
                        // la tâche n'est pas candidate mais elle le sera dès que la machine m sera libérée
                        pendingTasks[m][pendingTasksIndices[m]++] = newTask;
                    }
                }
            }
        }

        //  algorithme glouton donc sauf cas particulier on est pas sûr que c'est optimal
        return new Result(instance, sol.toSchedule(), Result.ExitCause.NotProvedOptimal);
    }
}

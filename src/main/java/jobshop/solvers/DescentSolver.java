package jobshop.solvers;

import jobshop.Instance;
import jobshop.Result;
import jobshop.Solver;
import jobshop.encodings.ResourceOrder;
import jobshop.encodings.Task;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class DescentSolver implements Solver {

    /** A block represents a subsequence of the critical path such that all tasks in it execute on the same machine.
     * This class identifies a block in a ResourceOrder representation.
     *
     * Consider the solution in ResourceOrder representation
     * machine 0 : (0,1) (1,2) (2,2)
     * machine 1 : (0,2) (2,1) (1,1)
     * machine 2 : ...
     *
     * The block with : machine = 1, firstTask= 0 and lastTask = 1
     * Represent the task sequence : [(0,2) (2,1)]
     *
     * */
    public final int priorityMode;
    static class Block {
        /** machine on which the block is identified */
        final int machine;
        /** index of the first task of the block */
        final int firstTask;
        /** index of the last task of the block */
        final int lastTask;

        Block(int machine, int firstTask, int lastTask) {
            this.machine = machine;
            this.firstTask = firstTask;
            this.lastTask = lastTask;
        }
    }

    public DescentSolver(int priorityMode) {
        this.priorityMode = priorityMode;
    }
    /**
     * Represents a swap of two tasks on the same machine in a ResourceOrder encoding.
     *
     * Consider the solution in ResourceOrder representation
     * machine 0 : (0,1) (1,2) (2,2)
     * machine 1 : (0,2) (2,1) (1,1)
     * machine 2 : ...
     *
     * The swam with : machine = 1, t1= 0 and t2 = 1
     * Represent inversion of the two tasks : (0,2) and (2,1)
     * Applying this swap on the above resource order should result in the following one :
     * machine 0 : (0,1) (1,2) (2,2)
     * machine 1 : (2,1) (0,2) (1,1)
     * machine 2 : ...
     */
    static class Swap {
        // machine on which to perform the swap
        final int machine;
        // index of one task to be swapped
        final int t1;
        // index of the other task to be swapped
        final int t2;

        Swap(int machine, int t1, int t2) {
            this.machine = machine;
            this.t1 = t1;
            this.t2 = t2;
        }

        /** Apply this swap on the given resource order, transforming it into a new solution. */
        public void applyOn(ResourceOrder order) {
            Task tmp = order.tasks[machine][t1];
            order.tasks[machine][t1] = order.tasks[machine][t2];
            order.tasks[machine][t2] = tmp;
        }
    }


    @Override
    public Result solve(Instance instance, long deadline) {
        GluttonousSolver solver = new GluttonousSolver(priorityMode);
        solver.solve(instance, deadline);
        ResourceOrder bestOrder = solver.sol;
        int bestMakespan = bestOrder.toSchedule().makespan();
        boolean continueDescent = true;
        while(continueDescent) {
            continueDescent = false;
            List<Swap> swaps = new ArrayList<Swap>();
            for(Block b : blocksOfCriticalPath(bestOrder)) {
                swaps.addAll(neighbors(b));
            }

            ResourceOrder tmpOrder = bestOrder.clone();
            for(Swap s : swaps) {
                s.applyOn(tmpOrder);
                int makespan = tmpOrder.toSchedule().makespan();
                if(makespan < bestMakespan) {
                    continueDescent = true;
                    bestMakespan = makespan;
                    bestOrder = tmpOrder.clone();
                }
                s.applyOn(tmpOrder);
            }
        }
        return new Result(instance, bestOrder.toSchedule(), Result.ExitCause.NotProvedOptimal);
    }

    /** Returns a list of all blocks of the critical path. */
    List<Block> blocksOfCriticalPath(ResourceOrder order) {
        List<Block> blocks = new ArrayList<>();
        List<Task> criticalPath = order.toSchedule().criticalPath();
        int machine = -1;
        int firstTask = 0;
        int consecutiveTasks = 0;
        for(Task t : criticalPath) {
            int m = order.instance.machine(t.job, t.task);
            if(m != machine) {
                if(consecutiveTasks >= 2) {
                    blocks.add(new Block(machine, firstTask, firstTask + consecutiveTasks - 1));
                }
                machine = m;
                firstTask = Arrays.asList(order.tasks[m]).indexOf(t);
                consecutiveTasks = 1;
            } else {
                consecutiveTasks++;
            }
        }
        if(consecutiveTasks >= 2) {
            blocks.add(new Block(machine, firstTask, firstTask + consecutiveTasks - 1));
        }
        return blocks;
    }

    /** For a given block, return the possible swaps for the Nowicki and Smutnicki neighborhood */
    List<Swap> neighbors(Block block) {
        List<Swap> neighbors = new LinkedList<>();
        if(block.lastTask == block.firstTask + 1) {
            neighbors.add(new Swap(block.machine, block.firstTask, block.lastTask));
        } else {
            neighbors.add(new Swap(block.machine, block.firstTask, block.firstTask + 1));
            neighbors.add(new Swap(block.machine, block.lastTask - 1, block.lastTask));
        }
        return neighbors;
    }

}
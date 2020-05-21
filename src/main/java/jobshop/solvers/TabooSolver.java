package jobshop.solvers;

import jobshop.Instance;
import jobshop.Result;
import jobshop.Solver;
import jobshop.encodings.ResourceOrder;
import jobshop.encodings.Task;

import java.util.*;

public class TabooSolver implements Solver {

    private final int maxIteration;
    private final int dureeTaboo;
    private final int priorityMode;

    public TabooSolver(int maxIteration, int dureeTaboo, int priorityMode) {
        this.maxIteration = maxIteration;
        this.dureeTaboo = dureeTaboo;
        this.priorityMode = priorityMode;
    }

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
        public <T> void applyOn(HashCodeArrayWrapper<Task> wrapper) {
            Task tmp = wrapper.get(machine, t1);
            wrapper.set(machine, t1, wrapper.get(machine, t2));
            wrapper.set(machine, t2, tmp);
        }
    }


    @Override
    public Result solve(Instance instance, long deadline) {
        Queue<HashCodeArrayWrapper<Task>> tabooQueue = new ArrayDeque<>(maxIteration);
        Set<HashCodeArrayWrapper<Task>> tabooSet = new HashSet<>(maxIteration);
        GluttonousSolver solver = new GluttonousSolver(priorityMode);
        solver.solve(instance, deadline);
        ResourceOrder bestOrder = solver.sol;
        ResourceOrder bestLocalOrder = solver.sol;
        int bestMakespan = bestOrder.toSchedule().makespan();
        int bestLocalMakespan;
        for(int iteration = 0; iteration < maxIteration; iteration++) {
            if(tabooQueue.size() >= dureeTaboo) {
                tabooSet.remove(tabooQueue.poll());
            }
            List<Swap> swaps = new ArrayList<Swap>();
            for(Block b : blocksOfCriticalPath(bestLocalOrder)) {
                swaps.addAll(neighbors(b));
            }
            bestLocalMakespan = Integer.MAX_VALUE;
            ResourceOrder tmpOrder = bestLocalOrder.clone();
            HashCodeArrayWrapper<Task> tmpWrapper = new HashCodeArrayWrapper<>(tmpOrder.tasks);
            for(Swap s : swaps) {
                // update solution and hashCode using wrapper
                s.applyOn(tmpWrapper);
                if(!tabooSet.contains(tmpWrapper)) {
                    int makespan = tmpOrder.toSchedule().makespan();
                    if(makespan < bestLocalMakespan) {
                        bestLocalMakespan = makespan;
                        bestLocalOrder = tmpOrder.clone();
                    }
                }
                s.applyOn(tmpWrapper);
            }
            HashCodeArrayWrapper<Task> tabooWrapper = new HashCodeArrayWrapper<>(bestLocalOrder.tasks);
            tabooQueue.offer(tabooWrapper);
            tabooSet.add(tabooWrapper);
            if(bestLocalMakespan < bestMakespan) {
                bestOrder = bestLocalOrder;
                bestMakespan = bestLocalMakespan;
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

    static class HashCodeArrayWrapper<T> {

        private static final List<Integer> powers = new ArrayList<>();

        static {
            powers.add(1);
        }

        private final T[][] array;
        private boolean hashCodeComputed = false;
        private int hashCode;
        private final int subArrayLength;
        private final int totalLength;

        HashCodeArrayWrapper(T[][] array) {
            this.array = array;
            subArrayLength = array[0].length;
            totalLength = array.length * subArrayLength;
            verifySizes();
        }

        void set(int i, int j, T val) {
            T old = array[i][j];
            int diff = objectHashCode(val) - objectHashCode(old);
            array[i][j] = val;
            hashCode += diff * getPower(totalLength - 1 - index(i, j));
        }

        T get(int i, int j) {
            return array[i][j];
        }

        int recomputeHashCode() {
            computeHashCode();
            return hashCode;
        }

        @Override
        public int hashCode() {
            if(!hashCodeComputed) {
                computeHashCode();
                hashCodeComputed = true;
            }
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if(this == obj) {
                return true;
            }
            if(!(obj instanceof HashCodeArrayWrapper)) {
                return false;
            }
            HashCodeArrayWrapper other = (HashCodeArrayWrapper) obj;
            for(int i = 0; i < array.length; i++) {
                if(!Arrays.deepEquals(array[i], other.array[i])) {
                    return false;
                }
            }
            return true;
        }

        private void verifySizes() {
            int size = array[0].length;
            for(Object[] a : array) {
                if(a.length != size) {
                    throw new IllegalArgumentException("Sub arrays lengths must equal");
                }
            }
        }

        private void computeHashCode() {
            int hashCode = 1;
            for(Object[] a : array) {
                for(Object o : a) {
                    hashCode = hashCode * 31 + objectHashCode(o);
                }
            }
            this.hashCode = hashCode;
        }

        private static int getPower(int index) {
            if(index >= powers.size()) {
                int lastPower = powers.get(powers.size() - 1);
                for(int i = powers.size(); i <= index; i++) {
                    lastPower *= 31;
                    powers.add(lastPower);
                }
            }
            return powers.get(index);
        }

        private int index(int i, int j) {
            return i * subArrayLength + j;
        }

        private int objectHashCode(Object val) {
            return (val == null ? 0 : val.hashCode());
        }
    }

}
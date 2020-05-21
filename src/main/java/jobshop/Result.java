package jobshop;

import java.util.Optional;

public class Result {

    public Result(Instance instance, Schedule schedule, ExitCause cause) {
        this.instance = instance;
        this.schedule = schedule;
        this.cause = cause;
    }

    public enum ExitCause {
        Timeout, ProvedOptimal, Blocked, NotProvedOptimal;
    }

    public final Instance instance;
    public final Schedule schedule;
    public final ExitCause cause;


}

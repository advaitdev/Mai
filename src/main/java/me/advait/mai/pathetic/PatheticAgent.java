package me.advait.mai.pathetic;

public final class PatheticAgent {

    private static final PatheticAgent INSTANCE = new PatheticAgent();
    private PatheticAgent() {}

    public static PatheticAgent getInstance() {
        return INSTANCE;
    }


}

package me.advait.mai.brain;

import me.advait.mai.brain.cerebrum.BrocasArea;
import me.advait.mai.brain.cerebrum.MotorCortex;
import me.advait.mai.brain.cerebrum.PrefrontalCortex;

public abstract class Brain {

    private final BrocasArea brocasArea;
    private final MotorCortex motorCortex;
    private final PrefrontalCortex prefrontalCortex;

    protected Brain(BrocasArea brocasArea, MotorCortex motorCortex, PrefrontalCortex prefrontalCortex) {
        this.brocasArea = brocasArea;
        this.motorCortex = motorCortex;
        this.prefrontalCortex = prefrontalCortex;
    }

    public BrocasArea getBrocasArea() {
        return brocasArea;
    }

    public MotorCortex getMotorCortex() {
        return motorCortex;
    }

    public PrefrontalCortex getPrefrontalCortex() {
        return prefrontalCortex;
    }

    // todo: code the brain lol


}

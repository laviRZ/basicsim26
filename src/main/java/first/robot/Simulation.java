package first.robot;

/**
 * The simulated world, moved one loop at a time.
 *
 * <p>Infrastructure. {@link Robot} calls {@link #step()} at the start of every loop, before the
 * command scheduler: each motor applies the voltage it was last given, the steering shafts and
 * mechanisms step their physics, the carpet moves the robot, and every motor logs where it now is.
 * Subsystems and commands then read this loop's state, whatever order they were created in. On a
 * real robot the world moves by itself, and this line is the only one that would go.
 */
final class Simulation {
  /** Advances every motor and the carpet by one loop, then logs every motor. */
  static void step() {
    Motor.applyAndStepAll();
    Carpet carpet = Carpet.existing();
    if (carpet != null) {
      carpet.step();
    }
    Motor.logAll();
  }

  /** Forgets every motor and the carpet, so a test can build a fresh robot. For tests only. */
  static void reset() {
    Motor.forgetAll();
    Motors.forgetAll();
    Carpet.discard();
  }

  private Simulation() {}
}

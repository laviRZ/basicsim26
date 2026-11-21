package first.robot;

import static edu.wpi.first.units.Units.Milliseconds;

import edu.wpi.first.units.measure.Time;

/** Robot-wide constants shared by the container and its infrastructure. */
public final class Constants {
  /** One control loop. */
  public static final Time LOOP_PERIOD = Milliseconds.of(20);

  /** Operator interface. */
  public static final class Driver {
    public static final int CONTROLLER_PORT = 0;

    private Driver() {}
  }

  private Constants() {}
}

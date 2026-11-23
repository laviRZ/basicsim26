package first.robot;

import static edu.wpi.first.units.Units.Meters;
import static edu.wpi.first.units.Units.Milliseconds;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.units.measure.Distance;
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

  /**
   * The carpet the robot drives on. Field coordinates put the origin in the blue driver station
   * corner, +x down the field and +y to the left, the frame AdvantageScope draws in.
   */
  public static final class Field {
    /** Wall to wall, along x. */
    public static final Distance LENGTH = Meters.of(16.541);

    /** Wall to wall, along y. */
    public static final Distance WIDTH = Meters.of(8.069);

    /**
     * Where the robot is set down before the match, facing +x. The simulated carpet starts the
     * robot here; odometry that does not start here disagrees with the carpet from the first loop.
     */
    public static final Pose2d START_POSE = new Pose2d(4.0, 4.0, Rotation2d.kZero);

    private Field() {}
  }

  private Constants() {}
}

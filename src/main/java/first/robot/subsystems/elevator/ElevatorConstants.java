package first.robot.subsystems.elevator;

import static edu.wpi.first.units.Units.Meters;

import edu.wpi.first.units.measure.Distance;

/** Elevator travel and tuning: a carriage on a drum, closed loop on position. */
public final class ElevatorConstants {
  public static final Distance MIN_HEIGHT = Meters.of(0.0);
  public static final Distance MAX_HEIGHT = Meters.of(0.26);

  /** Converts height error to volts; volts per metre. */
  public static final double KP = 20.0;

  /** Integral gain for WPILib's PIDController: volts per metre-second of accumulated error. */
  public static final double KI = 0.0;

  /** Derivative gain: volts per metre-per-second of error change. */
  public static final double KD = 0.0;

  /** The integral only accumulates while the error is smaller than this. */
  public static final Distance INTEGRAL_ZONE = Meters.of(0.01);

  /** How close counts as at the target. */
  public static final Distance TOLERANCE = Meters.of(0.002);

  private ElevatorConstants() {}
}

package first.robot.subsystems.elevator;

import static edu.wpi.first.units.Units.Meters;

import edu.wpi.first.units.measure.Distance;

/** Elevator travel and tuning: a carriage on a drum, closed loop on position. */
public final class ElevatorConstants {
  public static final Distance MIN_HEIGHT = Meters.of(0.0);
  public static final Distance MAX_HEIGHT = Meters.of(0.26);

  /** Converts height error to volts; volts per metre. */
  public static final double KP = 20.0;

  private ElevatorConstants() {}
}

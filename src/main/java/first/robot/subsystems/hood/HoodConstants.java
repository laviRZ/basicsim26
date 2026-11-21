package first.robot.subsystems.hood;

import static edu.wpi.first.units.Units.Radians;

import edu.wpi.first.units.measure.Angle;

/** Hood travel and tuning: pitch about the horizontal axis. */
public final class HoodConstants {
  public static final Angle MIN_PITCH = Radians.of(-0.262);
  public static final Angle MAX_PITCH = Radians.of(0.262);

  /** Converts pitch error to volts; volts per radian. */
  public static final double KP = 20.0;

  private HoodConstants() {}
}

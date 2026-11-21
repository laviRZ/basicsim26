package first.robot.subsystems.turret;

import static edu.wpi.first.units.Units.Radians;

import edu.wpi.first.units.measure.Angle;

/** Turret travel and tuning: yaw about the vertical axis, stopped by the cable wrap. */
public final class TurretConstants {
  public static final Angle MIN_YAW = Radians.of(-Math.PI);
  public static final Angle MAX_YAW = Radians.of(Math.PI);

  /** Converts yaw error to volts; volts per radian. */
  public static final double KP = 3.0;

  private TurretConstants() {}
}

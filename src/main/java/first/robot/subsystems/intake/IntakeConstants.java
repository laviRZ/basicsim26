package first.robot.subsystems.intake;

import static edu.wpi.first.units.Units.Radians;
import static edu.wpi.first.units.Units.Volts;

import edu.wpi.first.units.measure.Angle;
import edu.wpi.first.units.measure.Voltage;

/** Intake travel and tuning: a frame on a joint plus an open-loop roller. */
public final class IntakeConstants {
  public static final Angle FRAME_MIN_ANGLE = Radians.of(0.0);
  public static final Angle FRAME_MAX_ANGLE = Radians.of(Math.PI / 2);

  /** Converts angle error to volts; volts per radian. */
  public static final double FRAME_KP = 4.0;

  public static final Voltage ROLLER_VOLTAGE = Volts.of(6.0);

  private IntakeConstants() {}
}

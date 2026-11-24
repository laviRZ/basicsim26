package first.robot.subsystems.intake;

import static edu.wpi.first.units.Units.Radians;
import static edu.wpi.first.units.Units.RadiansPerSecond;
import static edu.wpi.first.units.Units.RadiansPerSecondPerSecond;
import static edu.wpi.first.units.Units.Volts;

import edu.wpi.first.units.measure.Angle;
import edu.wpi.first.units.measure.AngularAcceleration;
import edu.wpi.first.units.measure.AngularVelocity;
import edu.wpi.first.units.measure.Voltage;

/** Intake travel and tuning: a frame on a joint plus an open-loop roller. */
public final class IntakeConstants {
  public static final Angle FRAME_MIN_ANGLE = Radians.of(0.0);
  public static final Angle FRAME_MAX_ANGLE = Radians.of(Math.PI / 2);

  /** Converts angle error to volts; volts per radian. */
  public static final double FRAME_KP = 4.0;

  /** The motion profile's top speed for the frame. */
  public static final AngularVelocity FRAME_MAX_VELOCITY = RadiansPerSecond.of(3.0);

  /** How quickly the motion profile speeds the frame up and slows it down. */
  public static final AngularAcceleration FRAME_MAX_ACCELERATION =
      RadiansPerSecondPerSecond.of(10.0);

  public static final Voltage ROLLER_VOLTAGE = Volts.of(6.0);

  private IntakeConstants() {}
}

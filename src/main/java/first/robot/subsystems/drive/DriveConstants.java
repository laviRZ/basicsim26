package first.robot.subsystems.drive;

import static edu.wpi.first.units.Units.MetersPerSecond;
import static edu.wpi.first.units.Units.RadiansPerSecond;

import edu.wpi.first.units.measure.AngularVelocity;
import edu.wpi.first.units.measure.LinearVelocity;

/**
 * Swerve tuning: how fast the driver may ask the robot to go, and the gains that make each module
 * follow its command. The robot's geometry is not here: module locations, wheel radius and gearing
 * are facts in {@code RobotPhysics.Drive}.
 */
public final class DriveConstants {
  /** Top speed a full stick asks for; a little under what the motors can reach. */
  public static final LinearVelocity MAX_SPEED = MetersPerSecond.of(4.0);

  /** Top spin rate a full stick asks for. */
  public static final AngularVelocity MAX_ANGULAR_SPEED = RadiansPerSecond.of(2 * Math.PI);

  /** Converts steering error to volts; volts per radian. */
  public static final double STEER_KP = 6.0;

  /** Volts it takes to hold a wheel speed on its own; volts per metre per second. */
  public static final double DRIVE_KV = 2.5;

  /** Corrects what the feedforward misses; volts per metre per second of speed error. */
  public static final double DRIVE_KP = 1.0;

  /** Stick travel ignored around centre, as a fraction of full travel. */
  public static final double STICK_DEADBAND = 0.1;

  private DriveConstants() {}
}

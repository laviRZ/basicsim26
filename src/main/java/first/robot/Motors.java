package first.robot;

import first.robot.subsystems.elevator.ElevatorConstants;
import first.robot.subsystems.hood.HoodConstants;
import first.robot.subsystems.intake.IntakeConstants;
import first.robot.subsystems.turret.TurretConstants;

/**
 * The physical world the mechanisms run in: one motor per joint of the Block Bot, already
 * described.
 *
 * <p>Infrastructure. The plant — gearing, inertia, mass, gravity — is in {@link RobotPhysics};
 * travel and tuning live with the mechanism in its {@code <Name>Constants}. A subsystem asks for
 * its motor by name — {@code Motors.elevator()} — and gets the same seam real hardware would hand
 * it: volts in, position and velocity out. That is why {@link Motor}'s factories are
 * package-private.
 *
 * <p>Units follow the joint: radians for the feeder, the intake frame and roller, the hood and the
 * turret; metres for the carriage. Each motor logs itself under the name its javadoc gives.
 *
 * <p>The robot has one of each part, so each may be asked for once. {@link BlockBot} draws a part
 * from the motor handed out here; a part nobody has asked for sits at its starting position.
 *
 * <p>The swerve drivetrain is different in one way: its wheels stand on the carpet, so how fast a
 * wheel turns depends on the whole robot and on whether the wheel grips. Its motors and its gyro
 * come from the same place, the simulated carpet, and each may be asked for once.
 */
public final class Motors {
  /** The motors handed out so far, for {@link BlockBot}; {@code null} until asked for. */
  static Motor s_feederRoller;

  static Motor s_elevator;
  static Motor s_intakeFrame;
  static Motor s_intakeRoller;
  static Motor s_hood;
  static Motor s_turret;

  /** The feeder roller, logged as {@code Roller}. Open loop; position in radians. */
  public static Motor feederRoller() {
    once(s_feederRoller, "feeder roller");
    return s_feederRoller =
        Motor.flywheel(
            "Roller",
            RobotPhysics.MOTOR,
            RobotPhysics.FeederRoller.GEARING,
            RobotPhysics.FeederRoller.INERTIA,
            Constants.LOOP_PERIOD);
  }

  /**
   * The elevator carriage, logged as {@code Elevator}. Position in metres, between {@link
   * ElevatorConstants#MIN_HEIGHT} and {@link ElevatorConstants#MAX_HEIGHT}.
   */
  public static Motor elevator() {
    once(s_elevator, "elevator");
    return s_elevator =
        Motor.lift(
            "Elevator",
            RobotPhysics.MOTOR,
            RobotPhysics.Elevator.GEARING,
            RobotPhysics.Elevator.CARRIAGE_MASS,
            RobotPhysics.Elevator.DRUM_RADIUS,
            ElevatorConstants.MIN_HEIGHT,
            ElevatorConstants.MAX_HEIGHT,
            RobotPhysics.Elevator.GRAVITY,
            Constants.LOOP_PERIOD);
  }

  /** The intake frame, logged as {@code Intake/Frame}. Position in radians, 0 horizontal. */
  public static Motor intakeFrame() {
    once(s_intakeFrame, "intake frame");
    return s_intakeFrame =
        Motor.joint(
            "Intake/Frame",
            RobotPhysics.MOTOR,
            RobotPhysics.IntakeFrame.GEARING,
            RobotPhysics.IntakeFrame.INERTIA,
            RobotPhysics.IntakeFrame.LENGTH,
            IntakeConstants.FRAME_MIN_ANGLE,
            IntakeConstants.FRAME_MAX_ANGLE,
            RobotPhysics.IntakeFrame.GRAVITY,
            Constants.LOOP_PERIOD);
  }

  /** The intake roller, logged as {@code Intake/Roller}. Open loop; position in radians. */
  public static Motor intakeRoller() {
    once(s_intakeRoller, "intake roller");
    return s_intakeRoller =
        Motor.flywheel(
            "Intake/Roller",
            RobotPhysics.MOTOR,
            RobotPhysics.IntakeRoller.GEARING,
            RobotPhysics.IntakeRoller.INERTIA,
            Constants.LOOP_PERIOD);
  }

  /** The hood, logged as {@code Hood}. Position in radians, 0 level. */
  public static Motor hood() {
    once(s_hood, "hood");
    return s_hood =
        Motor.joint(
            "Hood",
            RobotPhysics.MOTOR,
            RobotPhysics.Hood.GEARING,
            RobotPhysics.Hood.INERTIA,
            RobotPhysics.Hood.LENGTH,
            HoodConstants.MIN_PITCH,
            HoodConstants.MAX_PITCH,
            RobotPhysics.Hood.GRAVITY,
            Constants.LOOP_PERIOD);
  }

  /**
   * The turret, logged as {@code Turret}. Position in radians, 0 forward; the walls are the cable
   * wrap, not the mechanism.
   */
  public static Motor turret() {
    once(s_turret, "turret");
    return s_turret =
        Motor.joint(
            "Turret",
            RobotPhysics.MOTOR,
            RobotPhysics.Turret.GEARING,
            RobotPhysics.Turret.INERTIA,
            RobotPhysics.Turret.RADIUS,
            TurretConstants.MIN_YAW,
            TurretConstants.MAX_YAW,
            RobotPhysics.Turret.GRAVITY,
            Constants.LOOP_PERIOD);
  }

  /**
   * A swerve module's drive motor, logged as {@code Drive/<Module>/Wheel}. Position and velocity
   * are the wheel's, in radians: multiply by {@link RobotPhysics.Drive#WHEEL_RADIUS} for distance
   * rolled. Modules are numbered 0 to 3 in the order of {@link
   * RobotPhysics.Drive#MODULE_LOCATIONS}.
   */
  public static Motor swerveDrive(int module) {
    return Carpet.get().claimDrive(module);
  }

  /**
   * A swerve module's steer motor, logged as {@code Drive/<Module>/Steer}. Position is the module's
   * heading relative to the robot, in radians: 0 rolls toward the robot's front, positive is
   * counter-clockwise seen from above. It is unwrapped — it keeps counting past a full turn — and
   * turns without limit.
   */
  public static Motor swerveSteer(int module) {
    return Carpet.get().claimSteer(module);
  }

  /** The gyro, mounted on the drivetrain. */
  public static Gyro gyro() {
    return Carpet.get().claimGyro();
  }

  private static void once(Motor existing, String part) {
    if (existing != null) {
      throw new IllegalStateException(
          "The "
              + part
              + " motor is already taken; ask Motors for it once and keep it in a field.");
    }
  }

  /** Forgets the motors handed out. For tests only, through {@link Simulation#reset()}. */
  static void forgetAll() {
    s_feederRoller = null;
    s_elevator = null;
    s_intakeFrame = null;
    s_intakeRoller = null;
    s_hood = null;
    s_turret = null;
  }

  private Motors() {}
}

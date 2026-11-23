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
 * turret; metres for the carriage.
 *
 * <p>The swerve drivetrain is different in one way: its wheels stand on the carpet, so how fast a
 * wheel turns depends on the whole robot and on whether the wheel grips. Its motors and its gyro
 * come from the same place, the simulated carpet, and each may be asked for once.
 */
public final class Motors {
  /** The feeder roller. Open loop; position in radians. */
  public static Motor feederRoller() {
    return Motor.flywheel(
        RobotPhysics.MOTOR,
        RobotPhysics.FeederRoller.GEARING,
        RobotPhysics.FeederRoller.INERTIA,
        Constants.LOOP_PERIOD);
  }

  /**
   * The elevator carriage. Position in metres, between {@link ElevatorConstants#MIN_HEIGHT} and
   * {@link ElevatorConstants#MAX_HEIGHT}.
   */
  public static Motor elevator() {
    return Motor.lift(
        RobotPhysics.MOTOR,
        RobotPhysics.Elevator.GEARING,
        RobotPhysics.Elevator.CARRIAGE_MASS,
        RobotPhysics.Elevator.DRUM_RADIUS,
        ElevatorConstants.MIN_HEIGHT,
        ElevatorConstants.MAX_HEIGHT,
        RobotPhysics.Elevator.GRAVITY,
        Constants.LOOP_PERIOD);
  }

  /** The intake frame. Position in radians, 0 horizontal. */
  public static Motor intakeFrame() {
    return Motor.joint(
        RobotPhysics.MOTOR,
        RobotPhysics.IntakeFrame.GEARING,
        RobotPhysics.IntakeFrame.INERTIA,
        RobotPhysics.IntakeFrame.LENGTH,
        IntakeConstants.FRAME_MIN_ANGLE,
        IntakeConstants.FRAME_MAX_ANGLE,
        RobotPhysics.IntakeFrame.GRAVITY,
        Constants.LOOP_PERIOD);
  }

  /** The intake roller. Open loop; position in radians. */
  public static Motor intakeRoller() {
    return Motor.flywheel(
        RobotPhysics.MOTOR,
        RobotPhysics.IntakeRoller.GEARING,
        RobotPhysics.IntakeRoller.INERTIA,
        Constants.LOOP_PERIOD);
  }

  /** The hood. Position in radians, 0 level. */
  public static Motor hood() {
    return Motor.joint(
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
   * The turret. Position in radians, 0 forward; the walls are the cable wrap, not the mechanism.
   */
  public static Motor turret() {
    return Motor.joint(
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
   * A swerve module's drive motor. Position and velocity are the wheel's, in radians: multiply by
   * {@link RobotPhysics.Drive#WHEEL_RADIUS} for distance rolled. Modules are numbered 0 to 3 in the
   * order of {@link RobotPhysics.Drive#MODULE_LOCATIONS}.
   */
  public static Motor swerveDrive(int module) {
    return Carpet.get().claimDrive(module);
  }

  /**
   * A swerve module's steer motor. Position is the module's heading relative to the robot, in
   * radians: 0 rolls toward the robot's front, positive is counter-clockwise seen from above. It is
   * unwrapped — it keeps counting past a full turn — and turns without limit.
   */
  public static Motor swerveSteer(int module) {
    return Carpet.get().claimSteer(module);
  }

  /** The gyro, mounted on the drivetrain. */
  public static Gyro gyro() {
    return Carpet.get().claimGyro();
  }

  private Motors() {}
}

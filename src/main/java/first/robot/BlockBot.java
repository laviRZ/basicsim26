package first.robot;

import static edu.wpi.first.units.Units.Meters;

import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Rotation3d;
import edu.wpi.first.math.geometry.Transform3d;
import java.util.function.DoubleSupplier;
import org.littletonrobotics.junction.Logger;

/**
 * Draws the Block Bot in AdvantageScope.
 *
 * <p>Infrastructure: students never call this and never touch a pose. A mechanism exposes its joint
 * state as plain numbers and {@link Robot} registers it once:
 *
 * <pre>
 *   m_blockBot.trackCarriage(m_elevator::getHeightMeters);
 * </pre>
 *
 * <p>Each tracker takes a supplier in radians (or metres where noted), evaluated once per loop;
 * every transform chain — the intake roller riding the frame, the hood riding the turret — is
 * computed here, not in mechanism code. An unregistered joint reads zero, so it sits at the model's
 * reference pose instead of collapsing to the origin.
 *
 * <p>The drivetrain needs no registration. Its pose and its modules are drawn from the simulated
 * {@link Carpet} — where the robot really is, how each module is really steered and how far each
 * wheel has really turned — not from anything a subsystem reports. Code that never computes a
 * position still sees the robot move, and odometry, when it exists, is a separate estimate to
 * compare against this one.
 *
 * <p>The array order is fixed by {@code assets/Robot_BlockBot/config.json}: entry {@code i} binds
 * {@code model_i.glb} to pose {@code i}.
 */
public final class BlockBot {
  /**
   * Fourteen poses, in {@code config.json} component order: the six mechanism parts, then the four
   * steering pods and the four wheels, each set front-left, front-right, back-left, back-right.
   */
  private static final String POSES_KEY = "BlockBot/Poses";

  /**
   * The frame every component hangs on: where the robot really is on the carpet. Before anything
   * asks for the drivetrain it stands still at {@link Constants.Field#START_POSE}.
   */
  private static final String ROBOT_POSE_KEY = "Robot/Pose";

  /** Where each joint sits in the model's zero configuration. */
  private static final double FEEDER_X = 0.23;

  private static final double FEEDER_Z = 0.21;
  private static final double CARRIAGE_X = -0.28;
  private static final double CARRIAGE_Z0 = 0.24;
  private static final double INTAKE_X = 0.30;
  private static final double INTAKE_Z = 0.24;
  private static final double INTAKE_ROLLER_OFFSET_X = 0.32;
  private static final double TURRET_X = -0.02;
  private static final double TURRET_Z = 0.30;
  private static final double HOOD_OFFSET_Z = 0.09;
  private static final double WHEEL_Z = RobotPhysics.Drive.WHEEL_RADIUS.in(Meters);

  private DoubleSupplier m_feederSpin = () -> 0.0;
  private DoubleSupplier m_carriageHeight = () -> 0.0;
  private DoubleSupplier m_intakeFrame = () -> 0.0;
  private DoubleSupplier m_intakeRoller = () -> 0.0;
  private DoubleSupplier m_turretYaw = () -> 0.0;
  private DoubleSupplier m_hoodPitch = () -> 0.0;

  /** Draws the feeder roller with the given spin angle, in radians at the roller. */
  public void trackFeeder(DoubleSupplier spinRadians) {
    m_feederSpin = spinRadians;
  }

  /** Draws the elevator carriage with the given height above its lowest position, in metres. */
  public void trackCarriage(DoubleSupplier heightMeters) {
    m_carriageHeight = heightMeters;
  }

  /**
   * Draws the intake: the frame's stow angle in radians (0 is flat, {@code PI/2} is upright) and
   * the roller's spin in radians.
   */
  public void trackIntake(DoubleSupplier frameRadians, DoubleSupplier rollerRadians) {
    m_intakeFrame = frameRadians;
    m_intakeRoller = rollerRadians;
  }

  /** Draws the turret at the given yaw, in radians. */
  public void trackTurret(DoubleSupplier yawRadians) {
    m_turretYaw = yawRadians;
  }

  /** Draws the hood at the given pitch, in radians. */
  public void trackHood(DoubleSupplier pitchRadians) {
    m_hoodPitch = pitchRadians;
  }

  /** Sends the robot frame and the fourteen component poses to AdvantageScope. */
  public void periodic() {
    Carpet carpet = Carpet.existing();
    Pose3d turret =
        new Pose3d(TURRET_X, 0, TURRET_Z, new Rotation3d(0, 0, m_turretYaw.getAsDouble()));
    Pose3d intakeFramePose =
        new Pose3d(INTAKE_X, 0, INTAKE_Z, new Rotation3d(0, -m_intakeFrame.getAsDouble(), 0));

    Pose3d[] poses = {
      new Pose3d(FEEDER_X, 0, FEEDER_Z, new Rotation3d(0, m_feederSpin.getAsDouble(), 0)),
      new Pose3d(CARRIAGE_X, 0, CARRIAGE_Z0 + m_carriageHeight.getAsDouble(), Rotation3d.kZero),
      intakeFramePose,
      intakeFramePose.transformBy(
          new Transform3d(
              INTAKE_ROLLER_OFFSET_X, 0, 0, new Rotation3d(0, -m_intakeRoller.getAsDouble(), 0))),
      turret,
      turret.transformBy(
          new Transform3d(0, 0, HOOD_OFFSET_Z, new Rotation3d(0, m_hoodPitch.getAsDouble(), 0))),
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null
    };
    for (int i = 0; i < 4; i++) {
      double steer = carpet == null ? 0 : carpet.steerAngle(i);
      double spin = carpet == null ? 0 : carpet.wheelAngle(i);
      Pose3d pod =
          new Pose3d(
              RobotPhysics.Drive.MODULE_LOCATIONS[i].getX(),
              RobotPhysics.Drive.MODULE_LOCATIONS[i].getY(),
              0,
              new Rotation3d(0, 0, steer));
      poses[6 + i] = pod;
      poses[10 + i] = pod.transformBy(new Transform3d(0, 0, WHEEL_Z, new Rotation3d(0, spin, 0)));
    }

    Logger.recordOutput(
        ROBOT_POSE_KEY, new Pose3d(carpet == null ? Constants.Field.START_POSE : carpet.pose()));
    Logger.recordOutput(POSES_KEY, poses);
  }
}

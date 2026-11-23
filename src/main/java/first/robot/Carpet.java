package first.robot;

import static edu.wpi.first.units.Units.Amps;
import static edu.wpi.first.units.Units.KilogramSquareMeters;
import static edu.wpi.first.units.Units.Kilograms;
import static edu.wpi.first.units.Units.Meters;
import static edu.wpi.first.units.Units.Seconds;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.system.plant.DCMotor;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.Subsystem;
import org.littletonrobotics.junction.Logger;

/**
 * The carpet: where the simulated robot really is, and why that is not where its sensors say.
 *
 * <p>Infrastructure, and package-private on purpose. On a real robot nothing reports the robot's
 * true position; code only has wheel encoders and a gyro, and must work the position out
 * (odometry). This class plays the world's part: it owns the four drive wheels, the four steering
 * shafts and the gyro, hands them out through {@link Motors}, and decides each loop how the robot
 * actually moved. {@link BlockBot} draws that true pose, so the robot drives on the field before
 * any odometry exists, and odometry written later can be compared against it.
 *
 * <p>The model, stepped in short substeps each loop:
 *
 * <ul>
 *   <li>Each drive motor pushes with its torque at the wheel, the current-limited motor model of
 *       {@link RobotPhysics#MOTOR}.
 *   <li>A wheel grips while the force asked of it, along and across its rolling direction, stays
 *       within {@link RobotPhysics.Drive#WHEEL_COF} times its quarter of the robot's weight. A
 *       gripping wheel turns exactly with the carpet under it.
 *   <li>Past that the wheel slips: the carpet pushes at most the friction limit, the wheel spins on
 *       its own, and its encoder counts distance the robot did not travel.
 *   <li>The robot is one rigid body: the module forces sum to a push and a twist on its mass and
 *       inertia, so modules that disagree scrub against each other instead of all being obeyed.
 *   <li>The bumpers stop at the field walls; the wheels do not know that.
 *   <li>The gyro integrates the true rotation with a small scale error.
 * </ul>
 */
final class Carpet {
  private static final String[] MODULE_NAMES = {
    "front-left", "front-right", "back-left", "back-right"
  };
  private static final int SUBSTEPS = 20;
  private static final double GRAVITY = 9.81;
  private static final double BATTERY_VOLTS = 12.0;

  /** How quickly sideways tread grip stops a sideways slide, seconds; tyre cornering stiffness. */
  private static final double TREAD_SETTLE = 0.03;

  private static Carpet s_carpet;

  private final double m_period = Constants.LOOP_PERIOD.in(Seconds);
  private final double m_mass = RobotPhysics.Drive.ROBOT_MASS.in(Kilograms);
  private final double m_inertia = RobotPhysics.Drive.ROBOT_INERTIA.in(KilogramSquareMeters);
  private final double m_wheelRadius = RobotPhysics.Drive.WHEEL_RADIUS.in(Meters);
  private final double m_halfLength = RobotPhysics.Drive.BUMPER_LENGTH.in(Meters) / 2;
  private final double m_halfWidth = RobotPhysics.Drive.BUMPER_WIDTH.in(Meters) / 2;
  private final double m_fieldLength = Constants.Field.LENGTH.in(Meters);
  private final double m_fieldWidth = Constants.Field.WIDTH.in(Meters);
  private final Translation2d[] m_modules = RobotPhysics.Drive.MODULE_LOCATIONS;

  private final Motor[] m_steer = new Motor[4];
  private final Wheel[] m_wheels = new Wheel[4];
  private final Motor[] m_drive = new Motor[4];
  private final boolean[] m_driveClaimed = new boolean[4];
  private final boolean[] m_steerClaimed = new boolean[4];
  private final Gyro m_gyro;
  private boolean m_gyroClaimed;

  /** True state, field frame: metres, radians, and their rates. */
  private double m_x;

  private double m_y;
  private double m_heading;
  private double m_vx;
  private double m_vy;
  private double m_omega;

  private double m_gyroYaw;
  private boolean m_wallContact;
  private final double[] m_slip = new double[4];

  /** The one carpet, created when the drivetrain's first part is asked for. */
  static Carpet get() {
    if (s_carpet == null) {
      s_carpet = new Carpet();
    }
    return s_carpet;
  }

  /**
   * Forgets the carpet so the next {@link #get()} builds a fresh one at the start pose. For tests
   * only: the caller must also clear the scheduler, which still holds the old one's step.
   */
  static void discard() {
    s_carpet = null;
  }

  /** The carpet if anything has asked for the drivetrain, else {@code null}. */
  static Carpet existing() {
    return s_carpet;
  }

  private Carpet() {
    for (int i = 0; i < 4; i++) {
      // The steering shafts step themselves, and register before the carpet, so each loop the
      // carpet sees this loop's steer angles.
      m_steer[i] =
          Motor.rotor(
              RobotPhysics.MOTOR,
              RobotPhysics.Drive.STEER_GEARING,
              RobotPhysics.Drive.STEER_INERTIA,
              Constants.LOOP_PERIOD);
      m_wheels[i] = new Wheel();
      m_drive[i] = Motor.steppedBy(m_wheels[i]);
    }
    m_gyro = new Gyro(() -> m_gyroYaw, () -> m_omega * (1 + RobotPhysics.Drive.GYRO_SCALE_ERROR));

    Pose2d start = Constants.Field.START_POSE;
    m_x = start.getX();
    m_y = start.getY();
    m_heading = start.getRotation().getRadians();

    CommandScheduler.getInstance()
        .registerSubsystem(
            new Subsystem() {
              @Override
              public void periodic() {
                Carpet.this.step();
              }
            });
  }

  Motor claimDrive(int module) {
    this.claim(m_driveClaimed, module, "drive motor");
    return m_drive[module];
  }

  Motor claimSteer(int module) {
    this.claim(m_steerClaimed, module, "steer motor");
    return m_steer[module];
  }

  Gyro claimGyro() {
    if (m_gyroClaimed) {
      throw new IllegalStateException("The robot has one gyro; ask Motors.gyro() once.");
    }
    m_gyroClaimed = true;
    return m_gyro;
  }

  private void claim(boolean[] claimed, int module, String what) {
    if (module < 0 || module >= 4) {
      throw new IllegalArgumentException(
          "Swerve modules are 0 to 3 (front-left, front-right, back-left, back-right); got "
              + module);
    }
    if (claimed[module]) {
      throw new IllegalStateException(
          "The " + MODULE_NAMES[module] + " " + what + " is already taken; ask for it once.");
    }
    claimed[module] = true;
  }

  /** Where the robot really is. */
  Pose2d pose() {
    return new Pose2d(m_x, m_y, new Rotation2d(m_heading));
  }

  /** The module's real steering angle, radians, 0 rolling toward the robot's front. */
  double steerAngle(int module) {
    return m_steer[module].getPosition();
  }

  /** How far the wheel has really turned, radians. */
  double wheelAngle(int module) {
    return m_wheels[module].m_angle;
  }

  private void step() {
    m_wallContact = false;
    double h = m_period / SUBSTEPS;
    for (int k = 0; k < SUBSTEPS; k++) {
      this.substep(h);
    }
    Logger.recordOutput("Carpet/SlipMetersPerSecond", m_slip);
    Logger.recordOutput("Carpet/WallContact", m_wallContact);
  }

  private void substep(double h) {
    double cos = Math.cos(m_heading);
    double sin = Math.sin(m_heading);
    double vxRobot = cos * m_vx + sin * m_vy;
    double vyRobot = -sin * m_vx + cos * m_vy;
    double friction = RobotPhysics.Drive.WHEEL_COF * m_mass * GRAVITY / 4;

    double fx = 0;
    double fy = 0;
    double torque = 0;
    for (int i = 0; i < 4; i++) {
      Wheel wheel = m_wheels[i];
      double px = m_modules[i].getX();
      double py = m_modules[i].getY();
      double dirX = Math.cos(this.steerAngle(i));
      double dirY = Math.sin(this.steerAngle(i));
      // Carpet velocity under the module, split along and across the wheel.
      double groundX = vxRobot - m_omega * py;
      double groundY = vyRobot + m_omega * px;
      double along = groundX * dirX + groundY * dirY;
      double across = -groundX * dirY + groundY * dirX;

      double motorTorque = wheel.torque();
      double push =
          wheel.m_gripping
              ? motorTorque / m_wheelRadius
              : friction * Math.signum(wheel.m_omega * m_wheelRadius - along);
      // Sideways the tread pushes back in proportion to how fast it slides, like a tyre's
      // cornering grip: a slide of its own share of the robot dies out in TREAD_SETTLE seconds.
      double side = -(m_mass / 4) * across / TREAD_SETTLE;
      double total = Math.hypot(push, side);
      if (total > friction) {
        push *= friction / total;
        side *= friction / total;
        wheel.m_gripping = false;
      }
      wheel.m_push = push;
      wheel.m_motorTorque = motorTorque;

      double forceX = push * dirX - side * dirY;
      double forceY = push * dirY + side * dirX;
      fx += forceX;
      fy += forceY;
      torque += px * forceY - py * forceX;
    }

    m_vx += (cos * fx - sin * fy) / m_mass * h;
    m_vy += (sin * fx + cos * fy) / m_mass * h;
    m_omega += torque / m_inertia * h;
    m_x += m_vx * h;
    m_y += m_vy * h;
    m_heading += m_omega * h;
    m_gyroYaw += m_omega * (1 + RobotPhysics.Drive.GYRO_SCALE_ERROR) * h;
    this.stopAtWalls();

    cos = Math.cos(m_heading);
    sin = Math.sin(m_heading);
    vxRobot = cos * m_vx + sin * m_vy;
    vyRobot = -sin * m_vx + cos * m_vy;
    double wheelInertia = RobotPhysics.Drive.WHEEL_INERTIA.in(KilogramSquareMeters);
    for (int i = 0; i < 4; i++) {
      Wheel wheel = m_wheels[i];
      double along =
          (vxRobot - m_omega * m_modules[i].getY()) * Math.cos(this.steerAngle(i))
              + (vyRobot + m_omega * m_modules[i].getX()) * Math.sin(this.steerAngle(i));
      if (wheel.m_gripping) {
        wheel.m_omega = along / m_wheelRadius;
        m_slip[i] = 0;
      } else {
        wheel.m_omega += (wheel.m_motorTorque - wheel.m_push * m_wheelRadius) / wheelInertia * h;
        double after = wheel.m_omega * m_wheelRadius - along;
        // Friction pushed the way the tread was sliding; once the tread stops sliding that way
        // it has caught the carpet and grips again.
        if (after * Math.signum(wheel.m_push) <= 0) {
          wheel.m_gripping = true;
          wheel.m_omega = along / m_wheelRadius;
          after = 0;
        }
        m_slip[i] = after;
      }
      wheel.m_angle += wheel.m_omega * h;
    }
  }

  /** Clamps the bumpers inside the field and stops any motion into a wall. */
  private void stopAtWalls() {
    double c = Math.abs(Math.cos(m_heading));
    double s = Math.abs(Math.sin(m_heading));
    double reachX = m_halfLength * c + m_halfWidth * s;
    double reachY = m_halfLength * s + m_halfWidth * c;
    double x = MathUtil.clamp(m_x, reachX, m_fieldLength - reachX);
    double y = MathUtil.clamp(m_y, reachY, m_fieldWidth - reachY);
    if (x != m_x) {
      m_vx = (x > m_x) == (m_vx > 0) ? m_vx : 0;
      m_x = x;
      m_wallContact = true;
    }
    if (y != m_y) {
      m_vy = (y > m_y) == (m_vy > 0) ? m_vy : 0;
      m_y = y;
      m_wallContact = true;
    }
  }

  /**
   * A drive wheel seen from its motor: volts in, wheel position and velocity out. The carpet steps
   * it, because how fast it turns depends on whether it grips.
   */
  private static final class Wheel implements Motor.Physics {
    private final DCMotor m_motor = RobotPhysics.MOTOR;
    private final double m_gearing = RobotPhysics.Drive.DRIVE_GEARING;
    private final double m_currentLimit = RobotPhysics.Drive.DRIVE_CURRENT_LIMIT.in(Amps);
    private double m_volts;
    private double m_omega;
    private double m_angle;
    private boolean m_gripping = true;
    private double m_push;
    private double m_motorTorque;

    /** Torque at the wheel from the motor at its present speed, after the current limit. */
    double torque() {
      double volts = MathUtil.clamp(m_volts, -BATTERY_VOLTS, BATTERY_VOLTS);
      double motorSpeed = m_omega * m_gearing;
      double current = (volts - motorSpeed / m_motor.KvRadPerSecPerVolt) / m_motor.rOhms;
      current = MathUtil.clamp(current, -m_currentLimit, m_currentLimit);
      return m_motor.KtNMPerAmp * current * m_gearing;
    }

    @Override
    public void setVoltage(double volts) {
      m_volts = volts;
    }

    @Override
    public void step() {}

    @Override
    public double position() {
      return m_angle;
    }

    @Override
    public double velocity() {
      return m_omega;
    }
  }
}

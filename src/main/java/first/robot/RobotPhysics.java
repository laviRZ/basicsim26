package first.robot;

import static edu.wpi.first.units.Units.Amps;
import static edu.wpi.first.units.Units.Inches;
import static edu.wpi.first.units.Units.KilogramSquareMeters;
import static edu.wpi.first.units.Units.Kilograms;
import static edu.wpi.first.units.Units.Meters;

import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.system.plant.DCMotor;
import edu.wpi.first.units.measure.Current;
import edu.wpi.first.units.measure.Distance;
import edu.wpi.first.units.measure.Mass;
import edu.wpi.first.units.measure.MomentOfInertia;

/**
 * The Block Bot's physical plant, in one table.
 *
 * <p>Infrastructure, and given: these numbers are what turn volts into encoder motion. A subsystem
 * never declares or edits them — it asks {@link Motors} for its motor — but they are written here,
 * named and unit-typed, so the simulated robot's behavior is readable instead of being buried in
 * simulator calls. Travel limits and control gains live with each mechanism in its {@code
 * <Name>Constants}.
 *
 * <p>Every quantity carries its unit; only the gearing ratio is a plain {@code double}.
 */
public final class RobotPhysics {
  /** Every joint runs one Kraken X60; the reduction is in the mechanism, not in the motor. */
  public static final DCMotor MOTOR = DCMotor.getKrakenX60(1);

  public static final class FeederRoller {
    public static final double GEARING = 3.0;
    public static final MomentOfInertia INERTIA = KilogramSquareMeters.of(0.015);

    private FeederRoller() {}
  }

  /** The elevator carriage: a mass on a drum, lifting against gravity. */
  public static final class Elevator {
    public static final double GEARING = 25.0;
    public static final Mass CARRIAGE_MASS = Kilograms.of(5.0);
    public static final Distance DRUM_RADIUS = Meters.of(0.020);
    public static final boolean GRAVITY = true;

    private Elevator() {}
  }

  /** The intake frame: an arm swinging against gravity. */
  public static final class IntakeFrame {
    public static final double GEARING = 40.0;
    public static final MomentOfInertia INERTIA = KilogramSquareMeters.of(0.05);
    public static final Distance LENGTH = Meters.of(0.34);
    public static final boolean GRAVITY = true;

    private IntakeFrame() {}
  }

  public static final class IntakeRoller {
    public static final double GEARING = 25.0;
    public static final MomentOfInertia INERTIA = KilogramSquareMeters.of(0.0005);

    private IntakeRoller() {}
  }

  /** The hood: an arm pitching against gravity. */
  public static final class Hood {
    public static final double GEARING = 30.0;
    public static final MomentOfInertia INERTIA = KilogramSquareMeters.of(0.02);
    public static final Distance LENGTH = Meters.of(0.20);
    public static final boolean GRAVITY = true;

    private Hood() {}
  }

  /** The turret: yaw about the vertical axis, where no gravity acts. */
  public static final class Turret {
    public static final double GEARING = 20.0;
    public static final MomentOfInertia INERTIA = KilogramSquareMeters.of(0.05);
    public static final Distance RADIUS = Meters.of(0.15);
    public static final boolean GRAVITY = false;

    private Turret() {}
  }

  /**
   * The swerve drivetrain and the carpet under it: four modules, each a drive motor turning a wheel
   * and a steer motor turning the module about its vertical axis.
   *
   * <p>The carpet is where the robot meets the world. Each wheel grips until the force asked of it
   * exceeds {@link #WHEEL_COF} times its share of the robot's weight; past that it slips, and its
   * encoder keeps counting distance the robot never travelled. The bumpers stop at the field walls
   * while the wheels keep turning. Both are why odometry drifts.
   */
  public static final class Drive {
    /**
     * Module centres relative to the robot centre, +x forward and +y left, in the order every
     * per-module array uses: front-left, front-right, back-left, back-right. This is the robot's
     * geometry — what kinematics needs — so read it rather than copying it.
     */
    public static final Translation2d[] MODULE_LOCATIONS = {
      new Translation2d(Meters.of(0.14), Meters.of(0.195)),
      new Translation2d(Meters.of(0.14), Meters.of(-0.195)),
      new Translation2d(Meters.of(-0.14), Meters.of(0.195)),
      new Translation2d(Meters.of(-0.14), Meters.of(-0.195))
    };

    public static final Distance WHEEL_RADIUS = Inches.of(2.0);

    /** Motor turns per wheel turn. */
    public static final double DRIVE_GEARING = 6.75;

    /** Motor turns per module turn. */
    public static final double STEER_GEARING = 150.0 / 7.0;

    /** Everything that turns with the wheel, seen at the wheel: wheel, gears and rotor. */
    public static final MomentOfInertia WHEEL_INERTIA = KilogramSquareMeters.of(0.002);

    /** Everything that turns when the module steers, seen at the module. */
    public static final MomentOfInertia STEER_INERTIA = KilogramSquareMeters.of(0.004);

    /** The whole robot: frame, mechanisms, battery and bumpers. */
    public static final Mass ROBOT_MASS = Kilograms.of(50.0);

    /** The whole robot about its vertical axis. */
    public static final MomentOfInertia ROBOT_INERTIA = KilogramSquareMeters.of(5.0);

    /** Bumper to bumper, front to back and side to side: what touches the wall. */
    public static final Distance BUMPER_LENGTH = Meters.of(0.86);

    public static final Distance BUMPER_WIDTH = Meters.of(0.86);

    /** Tread on carpet: the most a wheel can push, as a fraction of the weight on it. */
    public static final double WHEEL_COF = 1.1;

    /**
     * Each drive motor controller's stator current limit, which caps the torque a wheel can put on
     * the carpet. The current at which wheel torque matches the carpet's grip is the slip current;
     * this limit sits a little above it, as on many real robots, so a full-stick launch or a shove
     * against a wall breaks traction and the wheels spin.
     */
    public static final Current DRIVE_CURRENT_LIMIT = Amps.of(60.0);

    /**
     * How far the gyro's reading runs ahead of the real rotation, as a fraction: after the robot
     * turns once, the gyro has turned this much more than once. Real gyros are close but not exact.
     */
    public static final double GYRO_SCALE_ERROR = 0.002;

    private Drive() {}
  }

  private RobotPhysics() {}
}

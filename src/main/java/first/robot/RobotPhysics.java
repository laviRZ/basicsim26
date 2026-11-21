package first.robot;

import static edu.wpi.first.units.Units.KilogramSquareMeters;
import static edu.wpi.first.units.Units.Kilograms;
import static edu.wpi.first.units.Units.Meters;

import edu.wpi.first.math.system.plant.DCMotor;
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

  private RobotPhysics() {}
}

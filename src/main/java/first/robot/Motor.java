package first.robot;

import static edu.wpi.first.units.Units.KilogramSquareMeters;
import static edu.wpi.first.units.Units.Kilograms;
import static edu.wpi.first.units.Units.Meters;
import static edu.wpi.first.units.Units.Radians;
import static edu.wpi.first.units.Units.RadiansPerSecond;
import static edu.wpi.first.units.Units.Seconds;
import static edu.wpi.first.units.Units.Volts;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.system.plant.DCMotor;
import edu.wpi.first.math.system.plant.LinearSystemId;
import edu.wpi.first.units.measure.Angle;
import edu.wpi.first.units.measure.Distance;
import edu.wpi.first.units.measure.Mass;
import edu.wpi.first.units.measure.MomentOfInertia;
import edu.wpi.first.units.measure.Time;
import edu.wpi.first.units.measure.Voltage;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.RobotController;
import edu.wpi.first.wpilibj.simulation.DCMotorSim;
import edu.wpi.first.wpilibj.simulation.ElevatorSim;
import edu.wpi.first.wpilibj.simulation.FlywheelSim;
import edu.wpi.first.wpilibj.simulation.SingleJointedArmSim;
import java.util.ArrayList;
import java.util.List;
import org.littletonrobotics.junction.Logger;

/**
 * The motor a subsystem drives: volts in, position and velocity out.
 *
 * <p>Infrastructure. A subsystem never learns how the motor is simulated, and never describes one:
 * {@link Motors} already knows the plant from {@link RobotPhysics}, the way the physical world does
 * on a real robot. Nothing above {@link #setVoltage} changes when real hardware replaces the
 * simulation.
 *
 * <p>Units are the API at the seam: voltage goes in as a {@link Voltage}, while position and
 * velocity come out in base units — radians for a wheel or joint, metres for a lift — because the
 * dimension depends on the mechanism. Convert with {@code .in(...)} at the call site.
 *
 * <p>Like a real motor controller, it keeps the last voltage it was given until it is given
 * another, and it does not move while the robot is disabled. {@link Simulation} steps every motor
 * once per loop, so no mechanism calls {@code update}, and logs each one under its name: {@code
 * <Name>/Position<Unit>}, {@code <Name>/Velocity<Unit>PerSecond} and {@code <Name>/AppliedVolts}. A
 * mechanism logs only what its own code knows, such as its target.
 */
public final class Motor {
  /** Every motor, in the order it was made: the order {@link Simulation} steps them. */
  private static final List<Motor> s_all = new ArrayList<>();

  /** The unit names in each motor's log keys. */
  private static final String RADIANS = "Radians";

  private static final String METERS = "Meters";

  private final Physics m_physics;
  private final boolean m_stepsItself;
  private final String m_positionKey;
  private final String m_velocityKey;
  private final String m_voltsKey;
  private double m_requestedVolts;
  private double m_appliedVolts;

  /**
   * A spinning mass: rollers. Position in radians.
   *
   * <p>Package-private: a mechanism asks {@link Motors} for its motor rather than describing one.
   */
  static Motor flywheel(
      String name, DCMotor gearbox, double gearing, MomentOfInertia inertia, Time period) {
    return new Motor(
        name,
        RADIANS,
        new Wheel(
            new FlywheelSim(
                LinearSystemId.createFlywheelSystem(
                    gearbox, inertia.in(KilogramSquareMeters), gearing),
                gearbox),
            period.in(Seconds)));
  }

  /**
   * A single-axis joint: intake frame, hood, turret. Position in radians, 0 horizontal.
   *
   * @param length moment arm gravity acts on
   */
  static Motor joint(
      String name,
      DCMotor gearbox,
      double gearing,
      MomentOfInertia inertia,
      Distance length,
      Angle minAngle,
      Angle maxAngle,
      boolean gravity,
      Time period) {
    return new Motor(
        name,
        RADIANS,
        new Joint(
            new SingleJointedArmSim(
                gearbox,
                gearing,
                inertia.in(KilogramSquareMeters),
                length.in(Meters),
                minAngle.in(Radians),
                maxAngle.in(Radians),
                gravity,
                0.0),
            period.in(Seconds)));
  }

  /** A carriage on a drum: the elevator. Position in metres. */
  static Motor lift(
      String name,
      DCMotor gearbox,
      double gearing,
      Mass carriage,
      Distance drumRadius,
      Distance minHeight,
      Distance maxHeight,
      boolean gravity,
      Time period) {
    return new Motor(
        name,
        METERS,
        new Carriage(
            new ElevatorSim(
                gearbox,
                gearing,
                carriage.in(Kilograms),
                drumRadius.in(Meters),
                minHeight.in(Meters),
                maxHeight.in(Meters),
                gravity,
                0.0),
            period.in(Seconds)));
  }

  /**
   * A shaft that turns without limit and without load: a swerve module's steering. Position in
   * radians at the output, unwrapped — it keeps counting past a full turn.
   */
  static Motor rotor(
      String name, DCMotor gearbox, double gearing, MomentOfInertia inertia, Time period) {
    return new Motor(
        name,
        RADIANS,
        new Rotor(
            new DCMotorSim(
                LinearSystemId.createDCMotorSystem(
                    gearbox, inertia.in(KilogramSquareMeters), gearing),
                gearbox),
            period.in(Seconds)));
  }

  /**
   * A motor whose physics something else steps, because its load is not its own: a drive wheel,
   * whose speed the carpet and the rest of the robot decide together. Position in radians.
   */
  static Motor steppedBy(String name, Physics physics) {
    return new Motor(name, RADIANS, physics, false);
  }

  private Motor(String name, String unit, Physics physics) {
    this(name, unit, physics, true);
  }

  private Motor(String name, String unit, Physics physics, boolean stepsItself) {
    this.m_physics = physics;
    this.m_stepsItself = stepsItself;
    this.m_positionKey = name + "/Position" + unit;
    this.m_velocityKey = name + "/Velocity" + unit + "PerSecond";
    this.m_voltsKey = name + "/AppliedVolts";
    s_all.add(this);
  }

  /**
   * Gives every motor its voltage for this loop and steps those that step themselves. A disabled
   * robot applies nothing, whatever was asked, and no motor can apply more than the battery has.
   */
  static void applyAndStepAll() {
    boolean enabled = DriverStation.isEnabled();
    double battery = RobotController.getBatteryVoltage();
    for (Motor motor : s_all) {
      motor.m_appliedVolts =
          enabled ? MathUtil.clamp(motor.m_requestedVolts, -battery, battery) : 0.0;
      motor.m_physics.setVoltage(motor.m_appliedVolts);
      if (motor.m_stepsItself) {
        motor.m_physics.step();
      }
    }
  }

  /** Logs every motor's position, velocity and applied voltage, once everything has stepped. */
  static void logAll() {
    for (Motor motor : s_all) {
      Logger.recordOutput(motor.m_positionKey, motor.getPosition());
      Logger.recordOutput(motor.m_velocityKey, motor.getVelocity());
      Logger.recordOutput(motor.m_voltsKey, motor.m_appliedVolts);
    }
  }

  /** Forgets every motor. For tests only, through {@link Simulation#reset()}. */
  static void forgetAll() {
    s_all.clear();
  }

  /**
   * Commands the motor. It keeps this voltage until you set another or call {@link #stop()}, as a
   * real motor controller does; the simulation applies it from the next physics step.
   */
  public void setVoltage(Voltage volts) {
    m_requestedVolts = volts.in(Volts);
  }

  /** De-energizes the motor. */
  public void stop() {
    this.setVoltage(Volts.of(0.0));
  }

  /** Position at the mechanism: radians for a wheel or joint, metres for a lift. */
  public double getPosition() {
    return m_physics.position();
  }

  /** Velocity at the mechanism, matching {@link #getPosition} in units. */
  public double getVelocity() {
    return m_physics.velocity();
  }

  /** What a motor does. One implementation per kind of mechanism. */
  interface Physics {
    void setVoltage(double volts);

    void step();

    double position();

    double velocity();
  }

  private static final class Wheel implements Physics {
    private final FlywheelSim m_sim;
    private final double m_period;
    private double m_angle;

    Wheel(FlywheelSim sim, double period) {
      this.m_sim = sim;
      this.m_period = period;
    }

    @Override
    public void setVoltage(double volts) {
      m_sim.setInputVoltage(volts);
    }

    @Override
    public void step() {
      m_sim.update(m_period);
      // A flywheel has no position state; an encoder on the real roller would supply this.
      m_angle += m_sim.getAngularVelocity().in(RadiansPerSecond) * m_period;
    }

    @Override
    public double position() {
      return m_angle;
    }

    @Override
    public double velocity() {
      return m_sim.getAngularVelocity().in(RadiansPerSecond);
    }
  }

  private static final class Rotor implements Physics {
    private final DCMotorSim m_sim;
    private final double m_period;

    Rotor(DCMotorSim sim, double period) {
      this.m_sim = sim;
      this.m_period = period;
    }

    @Override
    public void setVoltage(double volts) {
      m_sim.setInputVoltage(volts);
    }

    @Override
    public void step() {
      m_sim.update(m_period);
    }

    @Override
    public double position() {
      return m_sim.getAngularPositionRad();
    }

    @Override
    public double velocity() {
      return m_sim.getAngularVelocityRadPerSec();
    }
  }

  private static final class Joint implements Physics {
    private final SingleJointedArmSim m_sim;
    private final double m_period;

    Joint(SingleJointedArmSim sim, double period) {
      this.m_sim = sim;
      this.m_period = period;
    }

    @Override
    public void setVoltage(double volts) {
      m_sim.setInputVoltage(volts);
    }

    @Override
    public void step() {
      m_sim.update(m_period);
    }

    @Override
    public double position() {
      return m_sim.getAngleRads();
    }

    @Override
    public double velocity() {
      return m_sim.getVelocityRadPerSec();
    }
  }

  private static final class Carriage implements Physics {
    private final ElevatorSim m_sim;
    private final double m_period;

    Carriage(ElevatorSim sim, double period) {
      this.m_sim = sim;
      this.m_period = period;
    }

    @Override
    public void setVoltage(double volts) {
      m_sim.setInputVoltage(volts);
    }

    @Override
    public void step() {
      m_sim.update(m_period);
    }

    @Override
    public double position() {
      return m_sim.getPositionMeters();
    }

    @Override
    public double velocity() {
      return m_sim.getVelocityMetersPerSecond();
    }
  }
}

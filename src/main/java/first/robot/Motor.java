package first.robot;

import static edu.wpi.first.units.Units.KilogramSquareMeters;
import static edu.wpi.first.units.Units.Kilograms;
import static edu.wpi.first.units.Units.Meters;
import static edu.wpi.first.units.Units.Radians;
import static edu.wpi.first.units.Units.RadiansPerSecond;
import static edu.wpi.first.units.Units.Seconds;
import static edu.wpi.first.units.Units.Volts;

import edu.wpi.first.math.system.plant.DCMotor;
import edu.wpi.first.math.system.plant.LinearSystemId;
import edu.wpi.first.units.measure.Angle;
import edu.wpi.first.units.measure.Distance;
import edu.wpi.first.units.measure.Mass;
import edu.wpi.first.units.measure.MomentOfInertia;
import edu.wpi.first.units.measure.Time;
import edu.wpi.first.units.measure.Voltage;
import edu.wpi.first.wpilibj.simulation.ElevatorSim;
import edu.wpi.first.wpilibj.simulation.FlywheelSim;
import edu.wpi.first.wpilibj.simulation.SingleJointedArmSim;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.Subsystem;

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
 * <p>The motor steps itself once per loop, so no mechanism calls {@code update}.
 */
public final class Motor {
  private final Physics m_physics;

  /**
   * A spinning mass: rollers. Position in radians.
   *
   * <p>Package-private: a mechanism asks {@link Motors} for its motor rather than describing one.
   */
  static Motor flywheel(DCMotor gearbox, double gearing, MomentOfInertia inertia, Time period) {
    return new Motor(
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
      DCMotor gearbox,
      double gearing,
      MomentOfInertia inertia,
      Distance length,
      Angle minAngle,
      Angle maxAngle,
      boolean gravity,
      Time period) {
    return new Motor(
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
      DCMotor gearbox,
      double gearing,
      Mass carriage,
      Distance drumRadius,
      Distance minHeight,
      Distance maxHeight,
      boolean gravity,
      Time period) {
    return new Motor(
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

  private Motor(Physics physics) {
    this.m_physics = physics;
    // A motor is not a command subsystem: it registers only its physics step, so it advances once
    // per scheduler loop and the subsystem that owns it never calls update().
    CommandScheduler.getInstance()
        .registerSubsystem(
            new Subsystem() {
              @Override
              public void periodic() {
                m_physics.step();
              }
            });
  }

  /** Commands the motor. Call it every loop while running; the voltage is not latched. */
  public void setVoltage(Voltage volts) {
    m_physics.setVoltage(volts.in(Volts));
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
  private interface Physics {
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

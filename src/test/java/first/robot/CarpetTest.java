package first.robot;

import static edu.wpi.first.units.Units.Meters;
import static edu.wpi.first.units.Units.Volts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wpi.first.hal.HAL;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj.simulation.DriverStationSim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The carpet's physics: what grips, what slips, and where the robot really ends up. */
class CarpetTest {
  private final double m_r = RobotPhysics.Drive.WHEEL_RADIUS.in(Meters);
  private Carpet m_carpet;
  private Motor[] m_drive;
  private Motor[] m_steer;
  private Gyro m_gyro;

  @BeforeEach
  void setUp() {
    assertTrue(HAL.initialize(500, 0));
    // A disabled robot applies no voltage, so the tests drive an enabled one.
    DriverStationSim.setEnabled(true);
    DriverStationSim.notifyNewData();
    Simulation.reset();
    m_drive = new Motor[4];
    m_steer = new Motor[4];
    for (int i = 0; i < 4; i++) {
      m_drive[i] = Motors.swerveDrive(i);
      m_steer[i] = Motors.swerveSteer(i);
    }
    m_gyro = Motors.gyro();
    m_carpet = Carpet.existing();
  }

  /** Runs loops with every module steered to its angle and driven at its voltage. */
  private void run(int loops, double[] angles, double[] volts) {
    for (int k = 0; k < loops; k++) {
      for (int i = 0; i < 4; i++) {
        double error = MathUtil.angleModulus(angles[i] - m_steer[i].getPosition());
        m_steer[i].setVoltage(Volts.of(MathUtil.clamp(12 * error, -12, 12)));
        m_drive[i].setVoltage(Volts.of(volts[i]));
      }
      Simulation.step();
    }
  }

  private double wheelDistance(int i) {
    return m_drive[i].getPosition() * m_r;
  }

  private double travelled() {
    return m_carpet
        .pose()
        .getTranslation()
        .minus(Constants.Field.START_POSE.getTranslation())
        .getNorm();
  }

  @Test
  void gentleDrivingGripsSoWheelsMeasureTheTruth() {
    double[] ahead = {0, 0, 0, 0};
    double[] gentle = {3, 3, 3, 3};
    this.run(100, ahead, gentle);
    assertTrue(this.travelled() > 1.0, "moved " + this.travelled());
    assertEquals(this.travelled(), this.wheelDistance(0), 0.01);
    assertEquals(0.0, m_carpet.pose().getY() - Constants.Field.START_POSE.getY(), 0.01);
  }

  @Test
  void aFullVoltageLaunchSlipsSoWheelsOverCount() {
    double[] ahead = {0, 0, 0, 0};
    double[] full = {12, 12, 12, 12};
    this.run(50, ahead, full);
    double over = this.wheelDistance(0) - this.travelled();
    assertTrue(over > 0.005, "wheel over-count " + over);
  }

  @Test
  void wallsStopTheBumpersButNotTheWheels() {
    double[] ahead = {0, 0, 0, 0};
    double[] cruise = {6, 6, 6, 6};
    this.run(400, ahead, cruise);
    double wall =
        Constants.Field.LENGTH.in(Meters) - RobotPhysics.Drive.BUMPER_LENGTH.in(Meters) / 2;
    assertEquals(wall, m_carpet.pose().getX(), 1e-6);
    assertTrue(this.wheelDistance(0) > this.travelled() + 1.0, "wheels keep counting");
  }

  @Test
  void spinningInPlaceTurnsTheGyroSlightlyTooFar() {
    double[] tangent = new double[4];
    for (int i = 0; i < 4; i++) {
      tangent[i] = RobotPhysics.Drive.MODULE_LOCATIONS[i].getAngle().getRadians() + Math.PI / 2;
    }
    this.run(25, tangent, new double[4]);
    this.run(150, tangent, new double[] {4, 4, 4, 4});
    double truth = m_carpet.pose().getRotation().getRadians();
    double turned = m_gyro.getYawRadians();
    assertTrue(Math.abs(turned) > 2 * Math.PI, "turned " + turned);
    assertEquals(0.0, this.travelled(), 0.05);
    double expected = Math.abs(turned) / (1 + RobotPhysics.Drive.GYRO_SCALE_ERROR);
    assertEquals(expected, Math.abs(this.unwrappedTruth(truth, turned)), 1e-3);
  }

  private double unwrappedTruth(double wrapped, double near) {
    double turns = Math.rint((near - wrapped) / (2 * Math.PI));
    return wrapped + turns * 2 * Math.PI;
  }

  @Test
  void modulesThatDisagreeScrubWithoutBlowingUp() {
    double[] crossed = {0, Math.PI / 2, Math.PI / 2, 0};
    this.run(25, crossed, new double[4]);
    this.run(200, crossed, new double[] {12, 12, 12, 12});
    assertTrue(Double.isFinite(m_carpet.pose().getX()));
    assertTrue(this.travelled() < 10, "travelled " + this.travelled());
  }
}

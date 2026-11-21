package first.robot.subsystems.roller;

import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Subsystem;
import first.robot.Motor;
import first.robot.Motors;
import org.littletonrobotics.junction.Logger;

/** Stage 1 solution: one motor on a roller, open loop. */
public class Roller implements Subsystem {
  private final Motor m_motor = Motors.feederRoller();

  public Roller() {
    // The motor registered its physics step first. Registering this subsystem afterwards keeps the
    // scheduler's order — step, then log — because periodics run in registration order.
    this.register();
  }

  public Command runCommand() {
    return this.run(() -> m_motor.setVoltage(RollerConstants.VOLTAGE))
        .finallyDo(m_motor::stop)
        .withName("Roller.Run");
  }

  public double getSpinRadians() {
    return m_motor.getPosition();
  }

  /** Posts this mechanism's state for AdvantageScope: where it is, how fast it is going. */
  @Override
  public void periodic() {
    Logger.recordOutput("Roller/PositionRadians", m_motor.getPosition());
    Logger.recordOutput("Roller/VelocityRadiansPerSecond", m_motor.getVelocity());
  }
}

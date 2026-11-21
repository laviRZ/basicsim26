package first.robot.subsystems.elevator;

import static edu.wpi.first.units.Units.Meters;
import static edu.wpi.first.units.Units.Volts;

import edu.wpi.first.units.measure.Distance;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Subsystem;
import first.robot.Motor;
import first.robot.Motors;
import org.littletonrobotics.junction.Logger;

/** Stage 2 solution: a carriage on a drum, closed loop on position. */
public class Elevator implements Subsystem {
  private final Motor m_motor = Motors.elevator();

  private Distance m_target = ElevatorConstants.MIN_HEIGHT;

  public Elevator() {
    // Without a default command the carriage just stops driving when a command ends. Setting one
    // also registers this subsystem — after the motor, so physics steps before this logs.
    this.setDefaultCommand(this.hold());
  }

  public Command goTo(Distance height) {
    return this.run(
            () -> {
              m_target = height;
              this.driveToTarget();
            })
        .withName("Elevator.GoTo");
  }

  private Command hold() {
    return this.run(this::driveToTarget).withName("Elevator.Hold");
  }

  private void driveToTarget() {
    m_motor.setVoltage(
        Volts.of(ElevatorConstants.KP * (m_target.in(Meters) - m_motor.getPosition())));
  }

  /** Height of the carriage above its bottom stop. */
  public double getHeightMeters() {
    return m_motor.getPosition();
  }

  /** Posts the reference and the measurement every loop: what was asked for, and where it is. */
  @Override
  public void periodic() {
    Logger.recordOutput("Elevator/TargetMeters", m_target.in(Meters));
    Logger.recordOutput("Elevator/PositionMeters", m_motor.getPosition());
  }
}

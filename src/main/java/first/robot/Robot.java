package first.robot;

import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.button.RobotModeTriggers;
import first.robot.subsystems.elevator.Elevator;
import first.robot.subsystems.elevator.ElevatorConstants;
import first.robot.subsystems.roller.Roller;
import org.littletonrobotics.junction.LoggedRobot;
import org.littletonrobotics.junction.Logger;
import org.littletonrobotics.junction.networktables.NT4Publisher;
import org.littletonrobotics.junction.wpilog.WPILOGWriter;

/**
 * Robot container: owns the mechanisms, publishes telemetry, and binds driver buttons.
 *
 * <p><b>Infrastructure.</b> The logging setup is written for you and no exercise requires changing
 * it. To add a mechanism, construct it beside the roller, register what it should draw, and bind
 * one of its commands in {@link #configureButtonBindings()}.
 *
 * <p>Every loop, in this order: the scheduler runs each subsystem's periodic method — first the
 * motor that steps its physics, then the mechanism that logs its state, in registration order —
 * then polls triggers and runs commands. Finally {@link BlockBot} samples the mechanisms and
 * publishes their poses.
 */
public class Robot extends LoggedRobot {
  private final BlockBot m_blockBot = new BlockBot();
  private final Roller m_roller = new Roller();
  private final Elevator m_elevator = new Elevator();
  private final CommandXboxController m_driverController =
      new CommandXboxController(Constants.Driver.CONTROLLER_PORT);

  public Robot() {
    // Register what each mechanism should draw. One line per mechanism; no geometry here.
    m_blockBot.trackFeeder(m_roller::getSpinRadians);
    m_blockBot.trackCarriage(m_elevator::getHeightMeters);

    this.configureButtonBindings();

    // Record metadata
    Logger.recordMetadata("ProjectName", BuildConstants.MAVEN_NAME);
    Logger.recordMetadata("BuildDate", BuildConstants.BUILD_DATE);
    Logger.recordMetadata("GitSHA", BuildConstants.GIT_SHA);
    Logger.recordMetadata("GitDate", BuildConstants.GIT_DATE);
    Logger.recordMetadata("GitBranch", BuildConstants.GIT_BRANCH);
    Logger.recordMetadata(
        "GitDirty",
        switch (BuildConstants.DIRTY) {
          case 0 -> "All changes committed";
          case 1 -> "Uncommitted changes";
          default -> "Unknown";
        });

    // NetworkTables is what AdvantageScope connects to; the .wpilog in logs/ can be opened later
    // without a live connection.
    Logger.addDataReceiver(new NT4Publisher());
    Logger.addDataReceiver(new WPILOGWriter());

    Logger.start();
  }

  /**
   * Binds the roller to the Xbox controller's A button and the elevator to X and Y, in teleop only.
   * Held, not toggled: releasing the button cancels the command, and the cancel clears the
   * commanded voltage.
   *
   * <p>Trigger bindings are polled on the scheduler's default button loop every loop, disabled
   * included, so the composed condition itself carries the mode. {@code teleop()} is {@code
   * isTeleopEnabled()} — mode and enabled — so the condition falls on disable and rises again when
   * teleop is re-enabled while the button is held; {@code whileTrue} cancels on the fall and
   * schedules on the rise. That is what makes a held button resume after a disable, with no
   * separate retry modifier.
   */
  private void configureButtonBindings() {
    RobotModeTriggers.teleop().and(m_driverController.a()).whileTrue(m_roller.runCommand());
    RobotModeTriggers.teleop()
        .and(m_driverController.x())
        .whileTrue(m_elevator.goTo(ElevatorConstants.MAX_HEIGHT));
    RobotModeTriggers.teleop()
        .and(m_driverController.y())
        .whileTrue(m_elevator.goTo(ElevatorConstants.MIN_HEIGHT));
  }

  /** This function is called periodically during all modes. */
  @Override
  public void robotPeriodic() {
    // Runs each subsystem's periodic (motor physics and mechanism state logging), then triggers,
    // commands and default commands.
    CommandScheduler.getInstance().run();

    // After the mechanisms have updated, send their poses to AdvantageScope.
    m_blockBot.periodic();
  }
}

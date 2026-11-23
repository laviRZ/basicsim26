package first.robot;

import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import org.littletonrobotics.junction.LoggedRobot;
import org.littletonrobotics.junction.Logger;
import org.littletonrobotics.junction.networktables.NT4Publisher;
import org.littletonrobotics.junction.wpilog.WPILOGWriter;

/**
 * Robot container: owns the mechanisms, publishes telemetry, and binds driver buttons.
 *
 * <p>Start with {@code subsystems/roller/Roller.java}: add the roller, construct it beside {@code
 * m_blockBot}, register it with {@code m_blockBot.trackFeeder(m_roller::getSpinRadians)}, and bind
 * its command in {@link #configureButtonBindings()}.
 *
 * <p>The swerve drivetrain needs no registration: {@link BlockBot} draws it from the simulated
 * carpet, where the robot really is, whatever the drive code believes.
 *
 * <p>Every loop, in this order: the scheduler runs each subsystem's periodic method — the motor
 * steps its physics, then the mechanism logs its state — then polls triggers and runs commands.
 * Finally {@link BlockBot} samples the mechanisms and publishes their poses.
 */
public class Robot extends LoggedRobot {
  private final BlockBot m_blockBot = new BlockBot();
  private final CommandXboxController m_driverController =
      new CommandXboxController(Constants.Driver.CONTROLLER_PORT);

  public Robot() {
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
   * Binds driver buttons. This is where a mechanism's commands go; the stage 1 binding runs the
   * roller while A is held:
   *
   * <pre>{@code
   * RobotModeTriggers.teleop().and(m_driverController.a()).whileTrue(m_roller.runCommand());
   * }</pre>
   */
  private void configureButtonBindings() {}

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

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
 * <p>Start with {@code subsystems/roller/}: write the {@code Roller} subsystem and its {@code
 * RunRoller} command, construct the roller beside {@code m_blockBot}, and bind its command in
 * {@link #configureButtonBindings()}. Default commands ({@code setDefaultCommand}) are set here
 * too.
 *
 * <p>Nothing registers with the 3D view: {@link BlockBot} draws each part from its motor, and the
 * swerve drivetrain from the simulated carpet, where the robot really is, whatever the drive code
 * believes.
 *
 * <p>Every loop, in this order: the simulated world steps — each motor applies its voltage, moves
 * and logs its state — then the scheduler runs each subsystem's periodic method, polls triggers and
 * runs commands. Finally {@link BlockBot} publishes the poses.
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
   * RobotModeTriggers.teleop().and(m_driverController.a()).whileTrue(new RunRoller(m_roller));
   * }</pre>
   */
  private void configureButtonBindings() {}

  /** This function is called periodically during all modes. */
  @Override
  public void robotPeriodic() {
    // The simulated world moves first: every motor turns with the voltage it was last given, and
    // logs its position, velocity and voltage. On a real robot this happens by itself.
    Simulation.step();

    // Then each subsystem's periodic, then triggers, commands and default commands.
    CommandScheduler.getInstance().run();

    // Send the poses to AdvantageScope.
    m_blockBot.periodic();
  }
}

package first.robot.subsystems.roller;

import static edu.wpi.first.units.Units.Volts;

import edu.wpi.first.units.measure.Voltage;

/** Roller tuning: open loop, so the only number is the voltage a button applies. */
public final class RollerConstants {
  public static final Voltage VOLTAGE = Volts.of(6.0);

  private RollerConstants() {}
}

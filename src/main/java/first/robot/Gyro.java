package first.robot;

import java.util.function.DoubleSupplier;

/**
 * The robot's gyro: how far it has turned since power-on.
 *
 * <p>Infrastructure, the same seam as {@link Motor}: a subsystem gets the one gyro from {@link
 * Motors#gyro()} and reads it like the real sensor. It measures rotation, not position — it never
 * knows where on the field the robot is.
 */
public final class Gyro {
  private final DoubleSupplier m_yaw;
  private final DoubleSupplier m_rate;

  Gyro(DoubleSupplier yaw, DoubleSupplier rate) {
    this.m_yaw = yaw;
    this.m_rate = rate;
  }

  /**
   * Rotation since power-on, radians, counter-clockwise positive seen from above. Unwrapped: it
   * keeps counting past a full turn.
   */
  public double getYawRadians() {
    return m_yaw.getAsDouble();
  }

  /** Rotation rate, radians per second, counter-clockwise positive. */
  public double getYawRateRadiansPerSecond() {
    return m_rate.getAsDouble();
  }
}

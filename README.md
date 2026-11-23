# Block Bot — WPILib 2026

A simulated FRC robot for learning command-based code: one Kraken X60 per joint, real mechanism
physics, a swerve drivetrain on a simulated carpet, and a 3D view of every joint in AdvantageScope.
Nothing is plugged in.

![Block Bot driving a figure eight while its mechanisms move](docs/images/blockbot-hero.gif)

The seven exercises — roller, elevator, intake, hood, turret, swerve, odometry — are in
[EXERCISES.md](EXERCISES.md). This page gets the simulator and the 3D view running; then you write the
first stage.

## What you need

- WPILib **2026** with VS Code. It installs Java, the WPILib simulator, and AdvantageScope.
- A terminal. The helper scripts are PowerShell.

## 1. Build

```powershell
git clone https://github.com/laviRZ/basicsim26.git
cd basicsim26
.\scripts\build.ps1
```

Wait for `BUILD SUCCESSFUL`.

## 2. Start the simulator

```powershell
.\scripts\sim.ps1
```

A **Robot Simulation** window opens. Before driving:

1. **Robot State** → click **Teleoperated**.
2. **System Joysticks** → drag **Keyboard 0** onto **Joystick[0]**.

![Clicking Teleoperated, dragging Keyboard 0 onto Joystick 0, then holding Z](docs/images/sim-setup.gif)

`Keyboard 0` turns four keys into controller buttons: **Z** is A, **X** is B, **C** is X, **V** is Y.

## 3. Add the 3D view

Open AdvantageScope (installed with WPILib), then:

1. **File → Connect to Simulator → NetworkTables 4 (AdvantageKit)** (or `Ctrl+Shift+K`).
2. **File → Import Layout…** and choose `ascope/BlockBot.json`.

Importing a layout closes the window. Open AdvantageScope again and repeat step 1: the Block Bot
tab is now saved.

![Block Bot 3D tab, dragged to orbit the robot](docs/images/ascope-3d.gif)

Drag to orbit, scroll to zoom. With no code written yet every joint sits at its reference pose and
the robot stands at its start position.

## 4. Write the roller

Add `src/main/java/first/robot/subsystems/roller/Roller.java`: the roller's motor, its command, and
`getSpinRadians()`.

Then in `Robot.java`:

- construct the roller beside `m_blockBot`,
- register its pose with `m_blockBot.trackFeeder(m_roller::getSpinRadians);`,
- bind its command in `configureButtonBindings()`:

```java
RobotModeTriggers.teleop().and(m_driverController.a()).whileTrue(m_roller.runCommand());
```

[EXERCISES.md](EXERCISES.md) stage 1 has the details.

## 5. Check it works

Hold **Z**. With a **Line Graph** tab (`Alt+G`) showing **Roller → PositionRadians** and
**Roller → VelocityRadiansPerSecond**, it should look like this:

![Roller position and velocity as Z is held and released](docs/images/roller-graph.gif)

Velocity jumps to about 105 rad/s while you hold Z, and position climbs steadily. Release Z: the
velocity falls back and the position flattens.

## Later: swerve and odometry

The last two stages drive the robot. The simulator plays the world's part: it decides where the robot
really goes on the carpet — wheels grip up to a limit and slip past it, bumpers stop at the walls —
and the 3D view draws that, so the robot drives before your code knows where it is. In the final
stage you work that out from wheel encoders and the gyro, and a green ghost shows your answer beside
the real robot:

![The ghost runs ahead on the launch and drives through the wall the robot stopped at](docs/images/odometry-wall.gif)

## If something does not work

- **Nothing moves.** The DriverStation panel must say `Robot Enabled: Yes`, `Joystick[0]` must show
  `16: Keyboard 0`, and you must be holding Z. Its first LED lights while Z is held.
- **No data in AdvantageScope.** Connect to the simulator again (`Ctrl+Shift+K`). Only one simulator
  can run at a time, so close any other Robot Simulation window first.
- **The robot is missing from the 3D view.** Restart AdvantageScope after starting the simulator.

# Block Bot — WPILib 2026 teaching skeleton

A simulated FRC robot for teaching: one Kraken X60 per joint, real mechanism physics behind a
`Motor` seam, AdvantageKit logging, and **Commands v2** on **WPILib 2026.2.1**. Nothing is plugged
in; every mechanism runs in the desktop simulator and draws itself in AdvantageScope.

![Block Bot in AdvantageScope](docs/images/ascope-3d.png)

This walkthrough goes from a clean checkout to the stage-1 roller spinning. The five exercise
stages — roller, elevator, intake, hood, turret — are in [EXERCISES.md](EXERCISES.md).

## Requirements

- WPILib **2026** (2026.2.1) with VS Code — it installs Java 17 and AdvantageScope.
- Python 3, used once by `tools/build_block_bot.py` to install the 3D model.
- Windows PowerShell for `scripts/*.ps1`; the Gradle build itself is cross-platform.

## 1. Clone and build

```powershell
git clone https://github.com/laviRZ/basicsim26.git
cd basicsim26
.\scripts\build.ps1
```

`build.ps1` resolves the JDK from `$env:JAVA_HOME`, else the WPILib JDK for `projectYear` in
`.wpilib/wpilib_preferences.json`, else the newest `%PUBLIC%\wpilib\*\jdk`. A successful run ends
with `BUILD SUCCESSFUL` after compile and `spotlessCheck`.

## 2. Start the simulator

```powershell
.\scripts\sim.ps1
```

That runs `gradlew simulateJava -PsimGui`. The Sim GUI extension is **off by default** in
`build.gradle`, because an empty HAL extension list is what AdvantageKit replay needs; `sim.ps1`
sets the `simGui` project property, GradleRIO marks `halsim_gui` default-enabled, and exports
`HALSIM_EXTENSIONS` for the forked JVM.

A **Robot Simulation** window opens. Two things to set before driving:

1. **Robot State** → click **Teleoperated** (the DriverStation panel then reads `Robot Enabled: Yes`).
2. **System Joysticks** → drag **Keyboard 0** onto **Joystick[0]**.

![Sim GUI with Teleoperated selected and Keyboard 0 assigned](docs/images/sim-teleop.png)

`Keyboard 0` maps four keys to the first four HAL buttons:

| Key | HAL button | Xbox equivalent | Bound command |
|---|---|---|---|
| **Z** | 1 | A | `Roller.runCommand()` |
| **X** | 2 | B | unbound |
| **C** | 3 | X | `Elevator.goTo(MAX_HEIGHT)` |
| **V** | 4 | Y | `Elevator.goTo(MIN_HEIGHT)` |

## 3. Connect AdvantageScope

Launch `%PUBLIC%\wpilib\2026\advantagescope\AdvantageScope (WPILib).exe`.

Once per machine, install the Block Bot model into AdvantageScope's asset folder (it is copied from
`assets/Robot_BlockBot/`; the viewer only reads that folder):

```powershell
python tools\build_block_bot.py
```

Connect with **File → Connect to Simulator → Default: NetworkTables 4 (AdvantageKit)**
(`Ctrl+Shift+K`). Then load the prepared tab with **File → Import Layout…** and pick
`ascope/BlockBot.json`. Importing a layout closes the window: open AdvantageScope again and
reconnect — the tab and camera persist.

The **Block Bot** tab draws the robot from `/RealOutputs/Robot/Pose` plus the six articulated
components in `/RealOutputs/BlockBot/Poses`:

![Block Bot 3D tab](docs/images/ascope-3d.png)

## 4. Hold Z — the roller runs

`Robot.configureButtonBindings()` binds A through a mode-composed trigger:

```java
RobotModeTriggers.teleop().and(m_driverController.a()).whileTrue(m_roller.runCommand());
```

With teleop enabled, hold **Z**. `Roller.runCommand()` applies `RollerConstants.VOLTAGE` every loop,
and `finallyDo` stops the motor when the command ends. Add **Roller → PositionRadians** and
**Roller → VelocityRadiansPerSecond** to a **Line Graph** tab (`Alt+G`), then hold Z for a few
seconds:

![Roller position and velocity while Z is held](docs/images/ascope-graph.png)

The velocity jumps to about 105 rad/s while the button is held, and the position — the encoder angle
that moves the roller in 3D — climbs linearly. Release Z and the command is cancelled, `finallyDo`
zeroes the motor, and both curves flatten.

## How the code is organised

| File | Role |
|---|---|
| `Motor.java` | the hardware seam: volts in, position/velocity out; the simulator lives behind it |
| `Motors.java` | one described motor per joint — gearing, inertia, mass, gravity come from `RobotPhysics` |
| `RobotPhysics.java` | the plant, in one unit-typed table |
| `BlockBot.java` | the six poses AdvantageScope draws; no mechanism touches a pose |
| `Robot.java` | receiver setup, mechanism fields, `m_blockBot.track*` calls, button bindings |
| `subsystems/<name>/` | a `Subsystem`, its commands, and its `<Name>Constants` |

Mechanisms register at the end of their constructor (`this.register()`, or implicitly through
`setDefaultCommand`), after the motor has registered its physics step: Commands v2 runs subsystem
`periodic()` methods in registration order, so the step precedes the state logging and the commands.

## Build, lint, simulate

```powershell
.\scripts\build.ps1    # compile + tests + spotlessCheck
.\scripts\lint.ps1     # spotlessCheck only (--fix applies)
.\scripts\sim.ps1      # desktop sim with the Sim GUI
```

Building also reformats: `build.gradle` wires `spotlessApply` into `compileJava`, so `lint.ps1` is
the real formatting gate.

## Troubleshooting

- **Nothing moves.** The DriverStation panel must read `Robot Enabled: Yes` and `Teleop Mode: Yes`,
  and `Joystick[0]` must show `16: Keyboard 0`. The first of the four LEDs under the joystick lights
  while Z is held.
- **AdvantageScope says "No data available".** Connect to the simulator again; the window title
  should read `127.0.0.1 — AdvantageScope` and the sidebar should show `RealOutputs`.
- **The port is busy.** Only one process can own NT4 port 5810. A leftover `gradlew simulateJava`
  keeps serving stale code; check with `netstat -ano | findstr :5810` and stop it.
- **The 3D robot is missing or stale.** Re-run `python tools\build_block_bot.py` and restart
  AdvantageScope — models are cached per session.

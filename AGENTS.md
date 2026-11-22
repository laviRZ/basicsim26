# AGENTS

FRC robot project and teaching skeleton: WPILib 2026 (`edu.wpi.first`, Commands v2, roboRIO deploy) +
AdvantageKit logging, **simulation only** — no hardware layer, no IO interfaces, no log replay. Robot code
in `src/main/java/first/robot`. Student-facing briefs: `EXERCISES.md`.

## Build / lint / sim (Windows PowerShell)

```powershell
.\scripts\build.ps1          # gradlew build: compile + spotlessCheck
.\scripts\lint.ps1           # spotlessCheck only; --fix to apply
.\scripts\sim.ps1            # desktop sim with Sim GUI; extra args go to Gradle
```

JDK: `scripts/_common.ps1` uses `$env:JAVA_HOME`, else the WPILib JDK for `.wpilib/wpilib_preferences.json`'s
`projectYear`, else the newest `%PUBLIC%\wpilib\*\jdk`. Project targets Java 17.

Stop the sim before `build.ps1`: a running sim holds the JNI natives in `build/jni/release` open, so
`extractReleaseNative` fails on a file lock.

## Structure: infrastructure vs student code

|Path|Owner|Rule|
|---|---|---|
|`Motor.java`|infrastructure|the hardware seam: volts in, position/velocity out; the sim lives behind it|
|`Motors.java`|infrastructure|the given physical world: one described motor per joint|
|`RobotPhysics.java`|infrastructure|the plant in one unit-typed table: gearing, inertia, mass, gravity; given, read-only|
|`BlockBot.java`|infrastructure|draws the six joints; holds every transform chain|
|`Robot.java`|infrastructure, one line per stage|receivers, mechanism fields, `m_blockBot.track*`, bindings|
|`Constants.java`|shared|robot-wide values: loop period, controller port|
|`subsystems/<name>/`|students|one `Subsystem` class, its commands, and its `<Name>Constants`|
|`assets/Robot_BlockBot/`|generated|never hand-edit; regenerate with the tool below|

A mechanism owns its motor and exposes joint state as plain numbers; it never constructs a `Pose3d`,
never imports `BlockBot`, and never imports `edu.wpi.first.wpilibj.simulation`. `Robot` registers each with
`m_blockBot.trackFeeder(m_roller::getSpinRadians)` and so on, and `BlockBot` alone knows the geometry
(including the intake-roller-on-frame and hood-on-turret chains). An unregistered joint reads zero, so
it sits at the model's reference pose.

**Physics is behind the motor seam, never in a subsystem.** `Motor` has three kinds — `flywheel`,
`joint`, `lift` — each wrapping a WPILib simulator (`FlywheelSim`, `SingleJointedArmSim`,
`ElevatorSim`) and stepping it once per loop; `getPosition` is radians for the first two and metres for
a lift. The factories are package-private: a subsystem asks `Motors` for its joint
(`Motors.elevator()`) and never declares gearing, inertia, mass or gravity. Those are in
`RobotPhysics`, one named, unit-documented table, so the volts-to-encoder relationship is visible to
students without being theirs to rewrite; each mechanism's travel and gain live in its own
`<Name>Constants`. That keeps student code hardware-shaped (`setVoltage`, `getPosition`, `stop`)
while the model comes from `edu.wpi.first.math.system.plant.DCMotor`'s real constants, gearing,
inertia, gravity, current draw and limit clamping (`LinearSystemId.createFlywheelSystem` for the
rollers). Adding a joint means adding a row to `RobotPhysics`; adding a fourth kind of mechanism
means adding a factory in `Motor`.

## Conventions

- Member fields are `m_camelCase`. A class calls its own methods with an explicit `this.`
  (`this.configureButtonBindings()`, `this.run(...)`, `this.setVoltage(Volts.of(0))`);
  static constants stay `UPPER_SNAKE_CASE`.
- Physical constants are unit-typed (`Distance`, `Angle`, `Voltage`, `MomentOfInertia`, `Mass`,
  `Time`), never bare doubles; convert with `.in(unit)` only at the `Motor` seam.
- `build.gradle` wires `spotlessApply` into `compileJava`, so builds silently reformat; `lint.ps1` is
  the real formatting gate.
- The Sim GUI is off in `build.gradle` (`wpi.sim.addGui().defaultEnabled` is false unless the
  `simGui` project property is set). `sim.ps1` passes `-PsimGui` to `simulateJava`, and GradleRIO
  turns a default-enabled extension into `HALSIM_EXTENSIONS` for the forked JVM. `halsim_ds_socket`
  stays disabled: enabling it hands DS state to a real DriverStation and removes the Sim GUI's own
  Robot State window.
- **Default commands are scheduled automatically.** `setDefaultCommand(cmd)` registers the subsystem
  and v2 runs the default whenever nothing else requires it, so a positional joint only needs an
  explicit `setDefaultCommand(hold())`; the default must require only its own subsystem. An
  open-loop subsystem that Binds nothing simply registers itself with `this.register()`.
- Bindings live in the `Robot` constructor, on the scheduler's default `EventLoop`, which is polled
  every loop — disabled included. `whileTrue` is what makes a held button resume after a disable:
  `RobotModeTriggers.teleop()` is `isTeleopEnabled()` (mode **and** enabled), so the composed
  condition falls on disable and rises again on re-enable, and `whileTrue` schedules on the rise and
  cancels on the fall. A disabled robot cancels commands that do not run while disabled, and
  `schedule()` refuses to start them. `finallyDo` is what puts a mechanism back at rest on cancel —
  it runs on interruption and on normal end alike.
- Control split: rollers are open loop (`setVoltage`); every positional joint is closed loop
  (`KP * (target − position)`), with `hold()` as the bound default command and `goTo(...)` as the
  move. A pure P loop settles below target when gravity is simulated — that steady-state error is
  deliberate teaching material, not a bug to paper over.
- Simulators enforce their limits (`Math.clamp` inside `update`); a command should still not ask for
  travel the mechanism does not have.
- A mechanism posts its own state by overriding `periodic()`, and registers at the end of its
  constructor — with `this.register()`, or implicitly through `setDefaultCommand`. Commands v2 calls
  `periodic()` every loop, and `CommandScheduler.run()` calls periodics in subsystem **registration
  order**; `Motor` registers its physics step the moment it is constructed, so registering the
  mechanism afterwards is what makes the step precede the log and the commands. It records
  `<Name>/Target<Unit>` where a target exists and `<Name>/Position<Unit>` (or velocity) always, as
  base-unit doubles. `Robot` never calls a mechanism to log.
- Comments say what the code cannot — units, physical meaning, why — and never restate a constant's
  value: the constant is the only source of truth, so a comment beside it silently rots.

## AdvantageScope

- Launch the WPILib 2026 build at `%PUBLIC%\wpilib\2026\advantagescope\AdvantageScope (WPILib).exe`
  (or the newer dev build in `~/Downloads/advantagescope-wpilib-win-x64`), then `File` > `Connect to
  Simulator` > `NetworkTables (AdvantageKit)`. The connection cannot be scripted; the layout
  persists across launches, the connection does not.
- Source keys are `/RealOutputs/<Key>` — no `NT:` prefix and no `AdvantageKit` segment — and that form
  works for both the live NT source and a `.wpilog` opened from `logs/`. `NT:/AdvantageKit/RealOutputs/...`
  resolves to nothing in either mode.
- `Robot/Pose` is published every loop at a point inside the field (the field origin is the driver
  station corner, where the model would intersect the wall). A constant pose is fine live: AdvantageScope
  subscribes with periodic resend, so the NT4 server keeps streaming the last value. In a log the pose
  has a single sample, so advance the playhead off `0s` before the first sample.
- The 3D tab is **two sources, not seven**: robot `/RealOutputs/Robot/Pose` (`Pose3d`, model `Block
  Bot` — the config `name`, not the folder) and one component `/RealOutputs/BlockBot/Poses`
  (`Pose3d[]`). Component pose `i` drives `model_i.glb`; per-component sources are neither needed
  nor accepted. `ascope/BlockBot.json` seeds the tab with both; importing a layout closes the
  window, so reconnect afterwards.
- `.wpilog` files are readable directly: WPILOG records are `[1-byte bitfield][entry id][payload
  length][timestamp][payload]`, with the field widths packed into the bitfield. Logged structs are raw
  bytes — `Pose3d` is 3 doubles then a quaternion (`w,x,y,z`) at offset 24. Reading `x` where `y`
  lives makes a spinning joint look still.
- Only one process can own NT4 port 5810. A leftover `gradlew simulateJava` keeps serving its own
  (stale) code and AdvantageScope silently stays connected to it; the new sim's console says
  `NT: NT4 server socket error: address already in use`. Check with `netstat -ano | findstr :5810`.
- The Sim GUI's Robot State panel only enables when the window is raised with no other AdvantageScope
  window overlapping it: `Disconnected` → click `Teleoperated` → click `Enable`.

## Block Bot model

`tools/build_block_bot.py` (stdlib only, `python tools/build_block_bot.py`) regenerates the model in
`assets/Robot_BlockBot/`: `model.glb` (chassis, wheels, elevator rails, turret pedestal, intake hinge)
plus `model_0..5.glb`, one articulated component each, in `config.json` component order (feeder roller,
carriage, intake frame, intake roller, turret, hood). Geometry is authored in robot axes (`+x` forward,
`+z` up, metres, floor at z=0) with empty `rotations`/`zeroedRotations` and every pivot at its file
origin.

The script validates each GLB by reading its buffers back (bounds, unit normals, winding, index range)
and proves turret/hood clearance rather than estimating it: yaw is unlimited, so only radius and height
about the yaw axis matter, and it intersects each moving part's convex `(r, z)` profile with every
static part's and the carriage's `(r, z)` box (separating-axis test). Current worst margins to the
elevator are 91.6 mm (turret) and 36.8 mm (hood); seated joints such as the bearing collar under the
yaw ring may touch, but nothing may interpenetrate.

**It also copies the set into `%APPDATA%\AdvantageScope\userAssets\Robot_BlockBot`, the only folder the
viewer reads; `scripts/sim.ps1` performs the same mirror on every run, so a fresh clone draws the robot
without running the generator. Regenerate after every geometry edit and restart AdvantageScope, which caches models per
session — editing the repo assets alone leaves the viewer drawing stale geometry against the new poses,
which looks exactly like parts colliding or floating off their pivots.**

Generated sim state (`simgui*.json`, `networktables.json`) and `logs/` are gitignored.

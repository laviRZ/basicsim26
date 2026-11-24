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
|`Motors.java`|infrastructure|the given physical world: one described motor per joint, plus the gyro|
|`Carpet.java`|infrastructure, package-private|the drivetrain's world: wheel grip and slip, walls, the true pose|
|`Gyro.java`|infrastructure|the gyro seam: yaw and yaw rate, nothing about position|
|`RobotPhysics.java`|infrastructure|the plant in one unit-typed table: gearing, inertia, mass, gravity; given, read-only|
|`BlockBot.java`|infrastructure|draws the six joints; holds every transform chain|
|`Robot.java`|infrastructure, one line per stage|receivers, mechanism fields, `m_blockBot.track*`, bindings|
|`Constants.java`|shared|robot-wide values: loop period, controller port, field size, start pose|
|`subsystems/<name>/`|students|one `Subsystem` class, its commands, and its `<Name>Constants`|
|`assets/Robot_BlockBot/`|generated|never hand-edit; regenerate with the tool below|

A mechanism owns its motor and exposes joint state as plain numbers; it never constructs a `Pose3d`,
never imports `BlockBot`, and never imports `edu.wpi.first.wpilibj.simulation`. `Robot` registers each with
`m_blockBot.trackFeeder(m_roller::getSpinRadians)` and so on, and `BlockBot` alone knows the geometry
(including the intake-roller-on-frame and hood-on-turret chains). An unregistered joint reads zero, so
it sits at the model's reference pose.

The drivetrain is the exception, on purpose: nothing registers it. `BlockBot` draws the robot pose,
the steering pods and the wheels from `Carpet` — where the robot really is, not where any subsystem
says — so the robot drives before students write odometry, and their odometry (`Drive/EstimatedPose`,
drawn as a ghost) is an estimate to compare against it. `Carpet` is package-private so student code
cannot read the truth, as on a real robot.

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
rollers). Adding a joint means adding a row to `RobotPhysics`; adding a new kind of mechanism
means adding a factory in `Motor`. Swerve adds two: `rotor` (`DCMotorSim`, unlimited travel, for
steering) and `steppedBy`, a motor whose physics another class steps — the drive wheels, which
`Carpet` steps because a wheel's speed depends on whether it grips.

**The carpet.** `Carpet` owns the four drive wheels, the four steer rotors and the gyro, created
together on the first `Motors.swerveDrive/swerveSteer/gyro` call (each may be claimed once) and
stepped after the steer rotors each loop, in 20 substeps. Each wheel pushes with the current-limited
motor torque; it grips until the force asked of it (along plus a proportional sideways cornering
force) leaves the friction circle of `WHEEL_COF` times a quarter of the weight, then slips at the
friction limit and spins on its own until it catches the carpet again. Module forces sum to one rigid
body; the bumpers clamp at the field walls with no motion into them; the gyro integrates true rotation
times `1 + GYRO_SCALE_ERROR`. `DRIVE_CURRENT_LIMIT` sits deliberately above the slip current (about
52 A here), so full-stick launches and wall shoves slip and odometry drifts — teaching material, not a
bug. `src/test/java/first/robot/CarpetTest.java` pins those behaviours.

## Conventions

- Member fields are `m_camelCase`. A class calls its own methods with an explicit `this.`
  (`this.configureButtonBindings()`, `this.register()`, `this.addRequirements(...)`);
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
- **Commands: classes, then factories.** Stages 1–2 of `EXERCISES.md` teach each command as a
  `Command` subclass in its mechanism's folder (`RunRoller`, `HoldElevator`, `MoveElevator`),
  overriding `initialize`, `execute`, `isFinished` and `end` and calling
  `this.addRequirements(subsystem)`; the subsystem exposes plain methods for them (`spin()`,
  `setTarget(...)`, `driveToTarget()`). A pause after stage 2 teaches lambdas, method references,
  suppliers, captured variables and the `Subsystem` factories (`run`, `runOnce`, `startRun`,
  `runEnd`, `startEnd`, plus `finallyDo`, `until`, `withName`), and has students rewrite stages 1–2
  as factories; from stage 3 on, commands are factories (`hold()`, `goTo(...)`, `spinRoller()`,
  `driveWithSticks(DoubleSupplier...)`). Student-facing text before the pause must not require
  lambdas, suppliers or factories; the `m_blockBot.track*(m_x::getY)` method references are copied
  as-is until the pause explains them.
- **Default commands are scheduled automatically.** `Robot` sets each positional joint's hold
  command (`new HoldElevator(...)`, later `m_x.hold()`) with `setDefaultCommand`, and v2 runs it whenever nothing else requires the subsystem;
  the default must require only its own subsystem. Every subsystem registers itself with
  `this.register()` as the last line of its constructor.
- Bindings live in the `Robot` constructor, on the scheduler's default `EventLoop`, which is polled
  every loop — disabled included. `whileTrue` is what makes a held button resume after a disable:
  `RobotModeTriggers.teleop()` is `isTeleopEnabled()` (mode **and** enabled), so the composed
  condition falls on disable and rises again on re-enable, and `whileTrue` schedules on the rise and
  cancels on the fall. A disabled robot cancels commands that do not run while disabled, and
  `schedule()` refuses to start them. A command's `end(interrupted)` (or `runEnd`/`finallyDo`) is
  what puts a mechanism back at rest on cancel — it runs on interruption and on normal end alike.
- Control split: rollers are open loop (`setVoltage`); every positional joint is closed loop
  (`KP * (target − position)`), with a hold command as the default and a move command that sets the
  target as it starts (`MoveElevator.initialize`, later `startRun` in `goTo(...)`). A pure P loop
  settles below target when gravity is simulated — that steady-state error is deliberate teaching
  material, not a bug to paper over. Step 2b replaces the elevator's line with `PIDController`: `KI`
  and `KD` ship at zero so the swap is invisible, then `KI` 20 shows windup (about 24 mm overshoot
  to the middle) and `INTEGRAL_ZONE` cures it. Step 3b moves the intake frame to
  `ProfiledPIDController` with `FRAME_MAX_VELOCITY`/`FRAME_MAX_ACCELERATION`, reset to the measured
  state in each command's `startRun` start, and `FRAME_KP` raised from its plain-P value to about 20.
  Those numbers were measured on the simulated plants; re-measure before changing the plant or gains.
- Simulators enforce their limits (`Math.clamp` inside `update`); a command should still not ask for
  travel the mechanism does not have.
- A mechanism posts its own state by overriding `periodic()`, and registers at the end of its
  constructor with `this.register()`. Commands v2 calls
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
- `Robot/Pose` is the carpet's true pose, published every loop; before the drivetrain exists it is
  `Constants.Field.START_POSE`, inside the field (the origin is the driver station corner, where the
  model would intersect the wall). A constant pose is fine live: AdvantageScope subscribes with
  periodic resend, so the NT4 server keeps streaming the last value. In a log a constant pose has a
  single sample, so advance the playhead off `0s` before the first sample.
- The 3D tab is **three sources, not fifteen**: robot `/RealOutputs/Robot/Pose` (`Pose3d`, model
  `Block Bot` — the config `name`, not the folder), one component `/RealOutputs/BlockBot/Poses`
  (`Pose3d[]`, fourteen entries), and a green ghost on `/RealOutputs/Drive/EstimatedPose` (`Pose2d`)
  that stays empty until students write odometry. Component pose `i` drives `model_i.glb`;
  per-component sources are neither needed nor accepted. `ascope/BlockBot.json` seeds the tab
  (camera `-2` is Orbit Robot, so a driving robot stays centred); importing a layout closes the
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
`assets/Robot_BlockBot/`: `model.glb` (chassis, bumpers, elevator rails, turret pedestal, intake hinge)
plus `model_0..13.glb`, one articulated component each, in `config.json` component order (feeder roller,
carriage, intake frame, intake roller, turret, hood, then four swerve steering pods and four wheels,
each front-left, front-right, back-left, back-right). Module positions in the generator must match
`RobotPhysics.Drive.MODULE_LOCATIONS`, which `BlockBot` and the carpet use. Geometry is authored in robot axes (`+x` forward,
`+z` up, metres, floor at z=0) with empty `rotations`/`zeroedRotations` and every pivot at its file
origin.

The script validates each GLB by reading its buffers back (bounds, unit normals, winding, index range)
and proves turret/hood clearance rather than estimating it: yaw is unlimited, so only radius and height
about the yaw axis matter, and it intersects each moving part's convex `(r, z)` profile with every
static part's and the carriage's `(r, z)` box (separating-axis test). Current worst margins to the
elevator are 68.7 mm (turret) and 36.8 mm (hood); seated joints such as the bearing collar under the
yaw ring may touch, but nothing may interpenetrate. Swerve modules steer without limit too, so each pod
and wheel's swept disc is checked against every static part, the carriage over its travel and the
feeder roller; the worst margin is 5.8 mm (a wheel to a side rail). The hood is also checked against
the turret it rides on: at every pitch, no hood vertex may dip into the turret disc or its front tab,
which is why the hood pivot sits on tall cheeks. Parts that share a joint are not otherwise checked, so
when editing a child part (the intake roller in its frame, a roller between its bearings) keep its ends
and radius clear of its parent by hand.

Within each GLB, `check_static` holds parts to butt joints: two parts may touch, and one may hide
entirely inside another, but none may poke into another (its crossing edges show) or share a
same-facing face with it (the two flicker as the renderer alternates). So the deck rests on the rails,
the side rails run between the front and rear rails, uprights stand on the deck under the crossbar,
and rollers and wheels show their spin with `striped()` wedges of alternating colour rather than
boxes pushed through a cylinder. Build new parts the same way.

**It also copies the set into `%APPDATA%\AdvantageScope\userAssets\Robot_BlockBot`, the only folder the
viewer reads; `scripts/sim.ps1` performs the same mirror on every run, so a fresh clone draws the robot
without running the generator. Regenerate after every geometry edit and restart AdvantageScope, which caches models per
session — editing the repo assets alone leaves the viewer drawing stale geometry against the new poses,
which looks exactly like parts colliding or floating off their pivots.**

Generated sim state (`simgui*.json`, `networktables.json`) and `logs/` are gitignored.

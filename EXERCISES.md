# Block Bot exercises

You are going to write the code for a robot that lives in a simulator. Each stage adds one part of
the robot, and you watch it move in AdvantageScope's 3D view. Nothing is plugged in, but the code is
the same code you would write for a real robot: you tell a motor how many volts to use, and you read
back where it is.

Get the simulator and the 3D view running first with the [README](README.md). It also walks you into
stage 1.

The stages, in order:

1. **Roller**: spin a motor while a button is held.
2. **Elevator**: move to a height and stay there.
3. **Intake**: two motors in one mechanism.
4. **Hood**: the elevator's idea again, but tilting.
5. **Turret**: turn the shooter around.
6. **Swerve**: drive the robot around the field.
7. **Odometry**: work out where the robot went.

Some words below will be new. Each one is explained the first time you need it, so you do not need to
know any of them before you start.

## Your files and the given files

You write your code in `src/main/java/first/robot/subsystems/`, one folder per mechanism. Each
folder already has a `<Name>Constants.java` with that mechanism's numbers (its limits, its voltage,
the gains you will tune), so read it before you start a stage. Your subsystem and its command classes
go in the same folder.

You also edit `Robot.java`. That is where you create each mechanism and connect it to buttons.

Everything else is given. Read it if you are curious, but you do not need to change it:

| File | What it does for you |
|---|---|
| `Motors` | hands you a motor for each part of the robot, and the gyro |
| `Motor` | the motor itself: you give it volts, it tells you where it is |
| `RobotPhysics` | how heavy and how geared each part is; the simulator uses these numbers |
| `BlockBot` | draws the robot in 3D |
| `Constants` | numbers for the whole robot, like where it starts on the field |

## The motor

You ask `Motors` for a motor. Each part of the robot has exactly one:

```java
Motor feeder   = Motors.feederRoller();   // position in radians
Motor carriage = Motors.elevator();       // position in metres
Motor frame    = Motors.intakeFrame();    // radians, 0 is flat
Motor roller   = Motors.intakeRoller();   // radians
Motor hood     = Motors.hood();           // radians, 0 is level
Motor turret   = Motors.turret();         // radians, 0 is facing forward
```

Every motor has the same four methods:

```java
feeder.setVoltage(Volts.of(6.0));   // push with 6 volts; call it again every loop
feeder.stop();                      // stop pushing
feeder.getPosition();               // where it is now (radians or metres)
feeder.getVelocity();               // how fast it is moving (radians or metres per second)
```

A radian is a way of measuring angles: a full turn is 2π radians, about 6.28, so half a turn is π.

The motor does not remember your voltage. Your code runs in a **loop**, 50 times a second, and a
running command must set the voltage on every pass.

## Buttons

`Keyboard 0` in the simulator has four buttons, named after the buttons of an Xbox controller:

| Controller button | Keyboard key |
|---|---|
| A | Z |
| B | X |
| X | C |
| Y | V |

Four buttons are not enough for five mechanisms, so **each stage takes the buttons over**. When you
start a new stage, comment out the previous stage's lines in `configureButtonBindings()`. A finished
mechanism keeps holding its position without them.

The stages use the buttons the same way: **Y** (top) means up, **A** (bottom) means down, **X**
(left) means left and **B** (right) means right.

## Seeing your work

```powershell
.\scripts\sim.ps1
```

In the simulator window, click **Teleoperated** and make sure Keyboard 0 is on Joystick[0]. In
AdvantageScope, connect with **File → Connect to Simulator** and open the **Block Bot** tab. The
[README](README.md) shows both steps.

A part you have not written yet stands still. To see numbers over time, open a **Line Graph** tab
(`Alt+G`) and drag in the values your code logs.

## Stage 1: Roller

The roller is a wheel that spins while you hold A. You write two classes for it, both in
`src/main/java/first/robot/subsystems/roller/`.

First, two words you will use in every stage:

- A **subsystem** is one mechanism of the robot: the roller, the elevator, the drivetrain. Its class
  owns that mechanism's motors, and gives the rest of the code simple methods like "spin" and "stop".
- A **command** is something the robot does for a while: spin the roller, move the elevator up. Each
  command is its own class. It says which subsystem it uses, and only one command can use a subsystem
  at a time.

### The subsystem: `Roller.java`

```java
public class Roller implements Subsystem {
  private final Motor m_motor = Motors.feederRoller();

  public Roller() {
    // Lets WPILib call periodic() for you every loop. Keep it the last line of the constructor.
    this.register();
  }

  public void spin() {
    // set the motor to RollerConstants.VOLTAGE
  }

  public void stop() {
    // stop the motor
  }

  /** How far the roller has turned, for the 3D view. */
  public double getSpinRadians() {
    // the motor's position
  }

  @Override
  public void periodic() {
    // Runs every loop, whatever command is running. Log the roller's state here:
    //   Logger.recordOutput("Roller/PositionRadians", ...);
    //   Logger.recordOutput("Roller/VelocityRadiansPerSecond", ...);
  }
}
```

### The command: `RunRoller.java`

A command class extends WPILib's `Command` and fills in up to four methods. WPILib calls them for you:

| Method | When WPILib calls it |
|---|---|
| `initialize()` | once, when the command starts |
| `execute()` | every loop while the command runs |
| `isFinished()` | every loop, after `execute()`; return `true` to end the command |
| `end(boolean interrupted)` | once, when the command stops; `interrupted` is `true` if something else stopped it |

You only write the ones you need. `RunRoller` keeps spinning until the button is let go, so it never
finishes by itself, and it stops the motor when it ends:

```java
public class RunRoller extends Command {
  private final Roller m_roller;

  public RunRoller(Roller roller) {
    m_roller = roller;
    // Tells WPILib this command uses the roller, so no other command can use it at the same time.
    this.addRequirements(roller);
  }

  @Override
  public void execute() {
    // spin the roller
  }

  @Override
  public void end(boolean interrupted) {
    // stop the roller
  }
}
```

### Wire it up

In `Robot.java`:

1. Create the roller next to `m_blockBot`: `private final Roller m_roller = new Roller();`
2. In the constructor, let the 3D view see it: `m_blockBot.trackFeeder(m_roller::getSpinRadians);`
   The `::` hands the 3D view the method itself, so it can ask for the angle every loop.
3. In `configureButtonBindings()`, run the command while A is held:

```java
RobotModeTriggers.teleop().and(m_driverController.a()).whileTrue(new RunRoller(m_roller));
```

`whileTrue` starts the command when you press A and stops it when you let go, which calls its `end`.
`RobotModeTriggers.teleop()` adds "and the robot is enabled in teleop", so a disabled robot never
spins.

**Done when:** holding A (Z on the keyboard) spins the roller up, and letting go makes it coast to a
stop. On the graph, velocity climbs, flattens, then falls back to zero.

The roller is **open loop**: your code pushes with a fixed voltage and never checks how fast the
roller really goes. That is fine for a roller. Every other mechanism needs to reach a position, and
for that your code has to look.

## Stage 2: Elevator

The elevator lifts a carriage up and down. Write it in `subsystems/elevator/`, and use
`Motors.elevator()` for its motor.

### Moving to a target

To reach a height, the code must keep checking where the carriage is and correct for it. That is
called **closed loop** control. The simplest version is one line:

```text
voltage = KP × (target − position)
```

The difference `target − position` is the **error**. Far from the target the error is big, so the
motor pushes hard. Close to the target it pushes gently, and at the target it stops pushing. `KP` (the
**proportional gain**) says how many volts to push with for each metre of error. You will tune it.

The subsystem remembers its target and knows that one line:

```java
public class Elevator implements Subsystem {
  private final Motor m_motor = Motors.elevator();
  private Distance m_target = ElevatorConstants.MIN_HEIGHT;

  public Elevator() {
    this.register();
  }

  public void setTarget(Distance height) {
    m_target = height;
  }

  /** Pushes towards the target. A command calls this every loop. */
  public void driveToTarget() {
    m_motor.setVoltage(
        Volts.of(ElevatorConstants.KP * (m_target.in(Meters) - m_motor.getPosition())));
  }

  // getHeightMeters() and periodic() as in stage 1
}
```

`Distance` is a number with a unit attached, so you cannot mix up metres and inches by mistake.
`m_target.in(Meters)` turns it into a plain number of metres, which is what the motor works in.

### Two commands

A mechanism that has to stay somewhere needs two commands:

- **`HoldElevator`** keeps the carriage at its target. Its `execute()` calls `driveToTarget()`, and
  that is all.
- **`MoveElevator`** takes a height in its constructor. Its `initialize()` sets the target, and its
  `execute()` calls `driveToTarget()`, so it keeps moving while the button is held.

`HoldElevator` becomes the elevator's **default command**: the one WPILib runs whenever no other
command is using the elevator. Set it in the `Robot` constructor:

```java
m_elevator.setDefaultCommand(new HoldElevator(m_elevator));
```

Let go of the button and `MoveElevator` ends. The elevator is free again, so `HoldElevator` starts
and keeps the carriage at the target `MoveElevator` set.

### Wire it up

- In `Robot`: `m_blockBot.trackCarriage(m_elevator::getHeightMeters);`
- Bind Y (V on the keyboard) to `new MoveElevator(m_elevator, ElevatorConstants.MAX_HEIGHT)` and A (Z)
  to `new MoveElevator(m_elevator, ElevatorConstants.MIN_HEIGHT)`, both with `whileTrue` like the
  roller.
- Log `Elevator/TargetMeters` and `Elevator/PositionMeters` from `periodic()`.

### Look closely

Hold Y and graph the target against the position. The carriage stops a little **below** the top.

Gravity pulls the carriage down all the time. The motor only pushes back as hard as the error is big,
so the carriage settles where the two balance, which has to be a little short of the target. That
leftover gap is called **steady-state error**. Try a bigger `KP` in `ElevatorConstants`: what happens
to the gap? Could anything else in the formula cancel gravity?

**Done when:** the carriage rises smoothly, stops near the top, stays there when you let go, and you
can explain the gap.

## Stage 3: Intake

The intake is a frame that swings up and down, with a roller at its end. Write it in
`subsystems/intake/`. It is one subsystem with **two motors**: `Motors.intakeFrame()` and
`Motors.intakeRoller()`.

- The frame works like the elevator, but in radians: 0 is flat on the floor and the top of its travel
  is upright. Give `Intake` a frame target and a `driveFrame()` method using
  `IntakeConstants.FRAME_KP`, and write `HoldIntake` and `MoveIntake` commands like the elevator's.
- The roller works like stage 1, at `IntakeConstants.ROLLER_VOLTAGE`: give `Intake` `spinRoller()`
  and `stopRoller()`, and write a `SpinIntake` command.
- Y (V) stows the frame upright at `FRAME_MAX_ANGLE`, A (Z) deploys it flat at `FRAME_MIN_ANGLE`,
  and holding X (C) spins the roller.

Wire it with `m_blockBot.trackIntake(m_intake::getFrameAngleRadians, m_intake::getRollerRadians);`
and log `Intake/FrameTargetRadians`, `Intake/FramePositionRadians` and
`Intake/RollerVelocityRadiansPerSecond`.

There is a trap here. Every one of these commands uses the intake, and only one can use it at a time.
When `SpinIntake` starts, `HoldIntake` stops, and nothing keeps the frame up. Try it and watch the
frame drop. Then make `SpinIntake`'s `execute()` keep driving the frame to its target too.

**Done when:** the frame swings to either end and stays there, and the roller spins without the frame
moving.

## Stage 4: Hood

The hood tilts up and down to aim. Write it in `subsystems/hood/`, with `Motors.hood()`.

This is the elevator's pattern again, measured in radians: 0 is level, and `HoodConstants` holds the
limits and `KP`. Gravity pulls the hood down, so with nothing holding it, it drops to its lower stop.

- Write `HoldHood` and `MoveHood`, and make `HoldHood` the default command.
- Y (V) aims up to `HoodConstants.MAX_PITCH`; A (Z) aims down to `HoodConstants.MIN_PITCH`.
- Wire it with `m_blockBot.trackHood(m_hood::getPitchRadians);` and log `Hood/TargetRadians` and
  `Hood/PositionRadians`.

**Done when:** the hood moves to either end and stays there with nothing pressed.

## Stage 5: Turret

The turret turns the whole shooter around. Write it in `subsystems/turret/`, with `Motors.turret()`.
Its angle is 0 facing forward and grows as it turns left.

Same pattern again, `HoldTurret` and `MoveTurret`, with `TurretConstants.KP`:

- X (C) turns it left to `TurretConstants.MAX_YAW`, B (X) turns it right to `TurretConstants.MIN_YAW`,
  and Y (V) brings it back to face forward, `Radians.of(0)`.
- Wire it with `m_blockBot.trackTurret(m_turret::getYawRadians);` and log `Turret/TargetRadians` and
  `Turret/PositionRadians`.

Two things to notice:

- **The turret gets all the way to its target.** The elevator and the hood stopped short. What is
  different about a turret?
- **Press X, then B.** Both ends point straight backwards, but the turret goes the long way round
  between them. A real turret has wires running through it, so it can only turn so far before they
  wind up. The limits stand for that.

**Done when:** the turret holds wherever you send it, and a full sweep from one end to the other
never touches the elevator.

## Optional: shorter commands

By now you have written a lot of small command classes that look almost the same. WPILib can build
simple commands for you, without a class. A subsystem's `this.run(...)` makes a command that requires
the subsystem and runs your code every loop, like an `execute()`; `.finallyDo(...)` adds code for when
it ends, like an `end()`. So the whole `RunRoller` class is the same as this method in `Roller`:

```java
public Command runCommand() {
  return this.run(this::spin).finallyDo(this::stop).withName("Roller.Run");
}
```

`withName` gives the command the name you see in the logs, which a class gets from its class name.
Try rewriting one stage this way. If you prefer classes, keep them: both are normal on real robots,
and the rest of these exercises work either way.

## Stage 6: Swerve drive

Now the robot drives. It has **swerve** wheels: four **modules**, one at each corner, and each module
has two motors. One spins the wheel to drive, and the other steers, turning the whole module to point
the wheel in any direction. Because every wheel can point anywhere, the robot can move in any
direction while facing any way.

This stage is longer than the others, so it comes in three steps. Each step drives the robot.

**The robot already knows how to be pushed around.** The 3D view draws the robot where the simulator
says it really is on the carpet, whatever your code thinks. You do not need to know where the robot
is to drive it; that is stage 7.

### What you are given

```java
Motor drive = Motors.swerveDrive(0);   // the wheel, in radians; module 0 is front-left
Motor steer = Motors.swerveSteer(0);   // which way the module points, in radians
Gyro gyro   = Motors.gyro();           // gyro.getYawRadians(): how far the robot has turned
```

The modules are numbered 0 to 3: front-left, front-right, back-left, back-right. Each part can be
asked for only once, because the robot has only one front-left wheel.

- **Steering**: 0 means the wheel rolls straight forward, and the angle grows as the module turns
  left. It keeps counting past a full turn: after two full turns it reads 4π, not 0.
- **Driving**: the wheel's position is in radians. Multiply by `RobotPhysics.Drive.WHEEL_RADIUS` (in
  metres) to get how far it has rolled; do the same to `getVelocity()` to get its speed in metres per
  second.

Speeds and gains are in `DriveConstants`.

### Step 6a: every wheel the same way

Write `subsystems/drive/Module.java`. A module is not a subsystem; it is a plain class that owns one
drive motor and one steer motor. Give it one method, `setState(SwerveModuleState state)`, called
every loop. A `SwerveModuleState` is a wheel speed in metres per second plus an angle (a
`Rotation2d`). The method drives each motor towards it:

- **Steering** is closed loop, like the turret:
  `STEER_KP * MathUtil.angleModulus(target − current)`, where `current` is the steer motor's
  position. `angleModulus` turns any angle into the shortest way round, between −π and π. Without it,
  a module that has turned twice would spin twice backwards to get to 0.
- **Driving** uses `DRIVE_KV * speed + DRIVE_KP * (speed − measured)`. The first part is a good guess
  at the voltage for that speed: `DRIVE_KV` volts for every metre per second. A guess like that is
  called a **feedforward**. The second part corrects whatever the guess gets wrong.

Then write `subsystems/drive/Drive.java`, the subsystem: four modules, in the order above, and the
gyro. Give it `setAllModules(SwerveModuleState state)`, which hands the same state to every module.

The command that drives from the sticks is `DriveWithSticks`. It needs the controller, so its
constructor takes the controller as well as the drive:

```java
public class DriveWithSticks extends Command {
  private final Drive m_drive;
  private final CommandXboxController m_controller;

  public DriveWithSticks(Drive drive, CommandXboxController controller) {
    m_drive = drive;
    m_controller = controller;
    this.addRequirements(drive);
  }

  @Override
  public void execute() {
    double x = MathUtil.applyDeadband(-m_controller.getLeftY(), DriveConstants.STICK_DEADBAND);
    double y = MathUtil.applyDeadband(-m_controller.getLeftX(), DriveConstants.STICK_DEADBAND);
    double speed = Math.hypot(x, y) * DriveConstants.MAX_SPEED.in(MetersPerSecond);
    Rotation2d angle = new Rotation2d(Math.atan2(y, x));
    m_drive.setAllModules(new SwerveModuleState(speed, angle));
  }
}
```

The minus signs are there because a stick reads negative when pushed forward and positive when
pushed right, while the robot counts forward and left as positive. The **deadband** treats a stick
that is nearly centred as exactly centred, so a real controller's slightly-off stick does not make
the robot creep.

In `Robot`, create `Drive` like the other mechanisms and make this its default command:

```java
m_drive.setDefaultCommand(new DriveWithSticks(m_drive, m_driverController));
```

`Drive` needs no `m_blockBot.track...` line: the 3D view already draws the drivetrain.

**Done when:** W, A, S and D slide the robot in every direction without it turning.

Two things look wrong, and step 6b fixes both:

- Go forward, then back. Every module spins half a turn before the robot changes direction.
- Let go of the keys. Every module snaps back to pointing forward.

### Step 6b: turning

To turn while driving, each wheel needs its own speed and angle. The maths that turns "how the robot
should move" into "what each wheel should do" is called **kinematics**, and WPILib does it for you.

How the robot should move is a `ChassisSpeeds`: forward speed, sideways speed (left is positive), and
turning speed (radians per second, left is positive). Give `Drive` a `drive(ChassisSpeeds speeds)`
method:

```java
// A field in Drive:
SwerveDriveKinematics m_kinematics = new SwerveDriveKinematics(RobotPhysics.Drive.MODULE_LOCATIONS);

// In drive(speeds):
// The wheels only change every loop, so driving while turning curves a little off course.
// discretize corrects for that.
speeds = ChassisSpeeds.discretize(speeds, Constants.LOOP_PERIOD.in(Seconds));
SwerveModuleState[] states = m_kinematics.toSwerveModuleStates(speeds);
// If one wheel would need to go faster than it can, slow all four down together.
SwerveDriveKinematics.desaturateWheelSpeeds(states, DriveConstants.MAX_SPEED);
// then give each module its own state
```

Then, in `Module.setState`, before driving the motors:

```java
Rotation2d current = new Rotation2d(m_steer.getPosition());
// Never turn more than a quarter turn: point the other way and drive backwards instead.
state.optimize(current);
// While the module is still turning, drive slower, so the wheel does not push sideways.
state.cosineScale(current);
```

Change `DriveWithSticks.execute()` to build a `ChassisSpeeds` and call `m_drive.drive(...)`. Forward
and sideways come from the left stick as before, times `MAX_SPEED`. Turning comes from the right stick:
`-m_controller.getRightX()`, through the deadband, times `DriveConstants.MAX_ANGULAR_SPEED`.

The keyboard needs a key for turning first. In the simulator window, open **DS → Keyboard 0
Settings**, raise the axis **Count** to 5, and give **Axis 4** a Decrease and an Increase key. Q and E
work, but take E away from Axis 2 first. Copy Axis 0's **Decay Rate**, so the turn stops when you
let go. A real controller needs none of this.

**Done when:** the robot drives and turns at the same time, reversing never turns a module more than
a quarter turn, and the modules stay put when you let go.

### Step 6c: field-relative driving

Turn the robot around and push forward. It drives towards you: "forward" is still the way the robot
faces. That is called **robot-relative** driving, and it is hard to drive that way.

In **field-relative** driving, forward on the stick always means away from you, whichever way the
robot faces. To do that, the code needs to know which way the robot faces, and that is what the
**gyro** measures. Give `Drive` a `getHeading()` method that returns
`new Rotation2d(m_gyro.getYawRadians())`. Then, in `DriveWithSticks`, one call converts the stick's
direction into the robot's:

```java
ChassisSpeeds speeds =
    ChassisSpeeds.fromFieldRelativeSpeeds(forward, left, turn, m_drive.getHeading());
```

**Done when:** W always drives the robot the way it faced when the simulator started, however it has
turned since.

### The carpet

The simulator does not move the robot exactly as your code asks. Each wheel grips the carpet only so
hard. Push harder than that (a full-speed start, or driving into a wall) and the wheel spins in place,
like a car's tyres on ice. The walls stop the robot, too.

Your code cannot see any of this, just as a real robot cannot. You can, though: graph
`Carpet/SlipMetersPerSecond` (how fast each wheel is spinning in place) and `Carpet/WallContact`
while you drive. It matters in the next stage.

Optional: log `Drive/ModuleStates` (what the modules are doing) and `Drive/ModuleSetpoints` (what you
asked for), each as a `SwerveModuleState[]`. AdvantageScope's **Swerve** tab draws them as arrows.

## Stage 7: Odometry

![The ghost runs ahead on the launch and drives through the wall the robot stopped at](docs/images/odometry-wall.gif)

So far, your code has no idea where the robot is on the field. **Odometry** works it out from the
inside: how far each wheel has rolled, which way each one points, and which way the gyro says the
robot faces. It is like walking with your eyes closed and counting steps.

Add it to `Drive`. WPILib's `SwerveDriveOdometry` does the maths:

```java
// In the constructor:
m_odometry = new SwerveDriveOdometry(
    m_kinematics, this.getHeading(), positions, Constants.Field.START_POSE);

// At the top of periodic(), every loop:
m_odometry.update(this.getHeading(), positions);
Logger.recordOutput("Drive/EstimatedPose", m_odometry.getPoseMeters());
```

`positions` holds one `SwerveModulePosition` per module: how far its wheel has rolled, in metres, and
which way it points. Give `Module` a method that returns one.

`START_POSE` is where the simulator puts the robot when it starts. Odometry only counts changes, so
it must start from the right place, or it is wrong from the first loop.

The Block Bot tab already shows a **green ghost** robot at `Drive/EstimatedPose`. The ghost is where
your code thinks the robot is; the solid robot is where it really is. Now try to pull them apart, and
explain each one:

- **Drive gently.** The ghost should stay on the robot.
- **Start from rest at full speed.** The wheels spin on the carpet before the robot gets going. They
  count distance the robot never travelled, so the ghost runs ahead.
- **Drive into a wall and keep pushing.** The robot stops; the wheels keep turning. The ghost drives
  through the wall.
- **Spin in place for ten turns.** The ghost slowly turns away from the robot. The gyro reads a
  little too much every turn (`RobotPhysics.Drive.GYRO_SCALE_ERROR`), and it adds up.

Then fix what you can:

- **Accelerate gently.** A `SlewRateLimiter` limits how fast a value may change. Give
  `DriveWithSticks` one per stick value, put each value through its limiter in `execute()`, and the
  wheels never ask the carpet for more grip than it has.
- **Reset when you know.** `m_odometry.resetPosition(...)` sets the estimate to a pose you know is
  right, for example at the start of a match.

Some drift cannot be fixed from wheels and a gyro alone. That is why real robots also use cameras to
look at the field.

**Stretch:** write the odometry yourself. Every loop, find how far each module moved since the last
loop and hand the four changes to `m_kinematics.toTwist2d(...)`. The result is a small step of the
robot, called a **twist**. Replace its turn with the gyro's change in angle, then move your pose with
`pose.exp(twist)`. Log your pose next to WPILib's and graph the difference.

**Done when:** the ghost follows the robot through ordinary driving, and you can make it drift on
purpose and say why every time.

## When something goes wrong

- **Pressing a button does nothing.** The simulator must be enabled in Teleoperated, Keyboard 0 must
  be on Joystick[0], and the binding must be in `configureButtonBindings()`. Check you did not
  comment it out when you moved on to a new stage, and that you are pressing the right key: A is Z.
- **It moves for an instant, then stops.** Either the voltage is set in `initialize()`, which runs
  once, instead of `execute()`, which runs every loop; or `isFinished()` returns `true`.
- **The motor keeps going after you let go.** The command's `end()` does not stop the motor.
- **The graph moves, but the 3D view does not.** You have not called `m_blockBot.track...` for it in
  `Robot`.
- **The 3D view moves, but there is nothing to graph.** `periodic()` is not running. Call
  `this.register()` at the end of the subsystem's constructor.
- **A mechanism falls when you let go.** It has no default command. Set its `Hold...` command with
  `setDefaultCommand` in `Robot`.
- **It falls when another of its commands starts.** Only one command can use a subsystem at a time.
  The new command must keep holding what the old one held.
- **Two mechanisms stop each other.** A command's `addRequirements` names a subsystem it does not
  need. Each command should require only the subsystem it drives.
- **It stops when you disable, and does not come back.** That is expected: disabling stops every
  command. Default commands come back by themselves when you enable again. A button you keep holding
  comes back too, because `RobotModeTriggers.teleop()` is part of its binding.
- **An error says a swerve part is already taken.** You asked `Motors` for the same module twice.
  Ask once and keep the motor in a field.
- **A module spins round and round.** Wrap the steering error with `MathUtil.angleModulus`.
- **Do not edit `Motor`, `Motors`, `RobotPhysics` or `BlockBot`.** No stage needs to, and the
  simulator depends on them.

## House style

The code in this project follows a few rules. Following them keeps your code easy to read next to
everyone else's:

- Fields start with `m_`: `m_motor`, `m_target`.
- A class calls its own methods with `this.`: `this.register()`, `this.addRequirements(...)`.
- Command classes are named for what they do, verb first: `RunRoller`, `MoveElevator`,
  `HoldElevator`.
- Constants are `UPPER_SNAKE_CASE` and live in your `<Name>Constants`.
- Physical values carry their unit (`Distance`, `Angle`, `Voltage`), and you turn them into plain
  numbers with `.in(Meters)` or `.in(Radians)` only where you hand them to a motor.
- Log keys are `<Mechanism>/<What><Unit>`, like `Elevator/PositionMeters`, so a graph always says
  what it shows.

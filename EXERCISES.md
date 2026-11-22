# Block Bot exercises

You are writing the subsystems and commands for a simulated block robot. Nothing is plugged in, but
the code you write is the code you would write on the real thing: a motor takes volts, an encoder
reports position, a command decides what to do about it.

Five stages, in order: **roller → elevator → intake → hood → turret**. Only the rollers are open
loop. Every joint with somewhere to be — elevator, intake frame, hood, turret — ends up closed loop.

## What you are given

| File | Yours? | What it is |
|---|---|---|
| `Motor` | no | the motor a subsystem drives: volts in, position and velocity out |
| `Motors` | no | the robot's physical world: one described motor per joint |
| `RobotPhysics` | no | the plant in one unit-typed table: gearing, inertia, mass, gravity |
| `BlockBot` | no | draws the robot; you never touch a pose |
| `Robot` | edit | constructs mechanisms, registers drawers, binds buttons |
| `subsystems/<name>/<Name>Constants.java` | tune | unit-typed travel and voltage; gains |
| `Constants` | no | robot-wide values: loop period, controller port |
| `assets/Robot_BlockBot/` | no | the 3D model |

There is no hardware layer, no IO interface, no replay. A mechanism owns its motors and its state, and
the physical behavior it runs against is given.

## The motor

```java
Motor feeder   = Motors.feederRoller();   // radians
Motor carriage = Motors.elevator();       // metres
Motor frame    = Motors.intakeFrame();    // radians, 0 flat
Motor roller   = Motors.intakeRoller();   // radians
Motor hood     = Motors.hood();           // radians, 0 level
Motor turret   = Motors.turret();         // radians, 0 forward

feeder.setVoltage(6.0);      // volts, every loop while running
feeder.stop();
feeder.getPosition();
feeder.getVelocity();
```

You do not describe your motor. Gearing, inertia, mass, gravity and travel are facts about the robot,
written down once in `Motors`; on a real robot the physical world plays that role, and none of your
code changes when hardware replaces the simulation. What is behind the seam is a WPILib simulator —
and you do not touch that either. The motor advances itself once per loop, so you never call `update`.

## The pattern

Every mechanism is the same three pieces:

1. `implements Subsystem`, owns its motor as a field.
2. A command sets voltage; `finallyDo` stops the motor when the command ends or is interrupted.
3. A getter exposes joint state as a plain number, and `Robot` registers it once:

```java
m_blockBot.trackFeeder(m_roller::getSpinRadians);
```

4. `periodic()` posts the mechanism's own state every loop — the target where there is one, and the
   measurement always. Call `this.register()` at the end of the constructor (or let
   `setDefaultCommand` register for you); `Robot` never asks for the state:

```java
public Elevator() {
  this.setDefaultCommand(this.hold());   // registers the subsystem, default command included
}

@Override
public void periodic() {
  Logger.recordOutput("Elevator/TargetMeters", m_target.in(Meters));
  Logger.recordOutput("Elevator/PositionMeters", m_motor.getPosition());
}
```

Registration order matters: the motor registered its physics step when the field was constructed, and
the scheduler calls periodics in that order, so the step happens before your log. A subsystem with no
default command registers itself with `this.register()`.

An open-loop mechanism has no target and logs only the measurement
(`Roller/PositionRadians`, `Roller/VelocityRadiansPerSecond`). Key names carry their units, and the
values are base units — metres, radians — because that is what `Motor` returns.

Field names take an `m_` prefix, and a mechanism calls its own methods with `this.`
(`this.driveToTarget()`, `this.run(...)`); static constants stay `UPPER_SNAKE_CASE`.
Physical constants are unit-typed — `Distance`, `Angle`, `Voltage` — and converted with `.in(Meters)`
only at the motor.

**Open loop** (rollers) has no feedback: hold the button, apply volts, done.

**Closed loop** (every joint) needs two commands — one that holds position and one that moves to a new
position — sharing one control law:

```java
private Distance m_target = ElevatorConstants.MIN_HEIGHT;

public Elevator() {
  this.setDefaultCommand(this.hold());   // stay where you are when nothing else runs
}

private Command hold() {
  return this.run(this::driveToTarget).withName("Elevator.Hold");
}

public Command goTo(Distance height) {
  return this.run(() -> {
        m_target = height;
        this.driveToTarget();
      })
      .withName("Elevator.GoTo");
}

private void driveToTarget() {
  m_motor.setVoltage(
      Volts.of(ElevatorConstants.KP * (m_target.in(Meters) - m_motor.getPosition())));
}
```

`hold()` is bound explicitly with `setDefaultCommand`. Commands v2 then runs it whenever no other
command requires the subsystem, so a joint that never binds one simply stops driving when its command
ends (and droops, if gravity is simulated).

## Seeing your work

```powershell
.\scripts\sim.ps1
```

Then AdvantageScope (`%PUBLIC%\wpilib\2026\advantagescope`, or the newer dev build in
`~/Downloads/advantagescope-wpilib-win-x64`), `File` > `Connect to Simulator` > `NetworkTables
(AdvantageKit)`, tab **Block Bot 3D**, and enable teleop in the Sim GUI.

A joint you have not registered stays at its reference pose, so a mechanism you have not written simply
does not move.

## Stage 1 — Roller

Write `src/main/java/first/robot/subsystems/roller/Roller.java`.

- `Motors.feederRoller()`, driven open loop at `RollerConstants.VOLTAGE`.
- `runCommand()` sets the voltage every loop and stops the motor in `finallyDo`, named `"Roller.Run"`.
- Expose `getSpinRadians()` and register it: `m_blockBot.trackFeeder(m_roller::getSpinRadians);`.
- Post `Roller/PositionRadians` and `Roller/VelocityRadiansPerSecond` from `periodic()`.
- Bind it to A: `RobotModeTriggers.teleop().and(m_driverController.a()).whileTrue(...)`.

Done when: holding A spins the roller up — you can see the ramp — and releasing it coasts down.

## Stage 2 — Elevator

New file `subsystems/elevator/Elevator.java`.

- `Motors.elevator()`: the carriage travels **0 to 0.26 m**, 25:1 gearbox, 5 kg carriage, 20 mm
  drum, lifting against gravity. The plant is in `RobotPhysics`, the limits in `ElevatorConstants`;
  you declare neither.
- Hold X → `goTo(ElevatorConstants.MAX_HEIGHT)`, hold Y → `goTo(ElevatorConstants.MIN_HEIGHT)`.
  Tune `ElevatorConstants.KP` (preset near 20 V/m).
- With gravity in the model, a pure P loop settles slightly below its target. That steady-state error
  is the point — think about what fixes it.

Wire it: `m_blockBot.trackCarriage(m_elevator::getHeightMeters);` and post
`Elevator/TargetMeters` alongside `Elevator/PositionMeters`. Graph the two: the gap that remains
at the top of the move is the steady-state error below.

Done when: the carriage rises smoothly, stops at the top, holds when you let go, and settles only
slightly under a commanded height.

## Stage 3 — Intake

New file `subsystems/intake/Intake.java`. One mechanism, **two motors** — this is where the two kinds
of control meet.

- Frame: `Motors.intakeFrame()`, **0 rad flat to π/2 upright**, gravity on.
- Roller: `Motors.intakeRoller()`, open loop at `IntakeConstants.ROLLER_VOLTAGE`.
- Hold A → stow (`IntakeConstants.FRAME_MAX_ANGLE`), hold Y → deploy
  (`IntakeConstants.FRAME_MIN_ANGLE`), hold X → spin the roller. Tune `IntakeConstants.FRAME_KP`
  (preset near 4 V/rad).

Wire it: `m_blockBot.trackIntake(m_intake::getFrameAngleRadians, m_intake::getRollerRadians);` and
post `Intake/FrameTargetRadians`, `Intake/FrameRadians`, and `Intake/RollerRadiansPerSecond`.

Done when: the frame swings and holds at either end, and the roller spins without moving the frame.

## Stage 4 — Hood

New file `subsystems/hood/Hood.java`.

- `Motors.hood()`, pitch **±15°** (±0.262 rad), gravity on — unpowered, it droops to the lower stop.
- Hold X → aim up (`HoodConstants.MAX_PITCH`), hold B → aim down (`HoodConstants.MIN_PITCH`);
  nothing held, the hold command keeps it there. Tune `HoodConstants.KP` (preset near 20 V/rad).

Wire it: `m_blockBot.trackHood(m_hood::getPitchRadians);` and post `Hood/TargetRadians` alongside
`Hood/PitchRadians`.

Done when: the hood holds an angle with nothing pressed, and droops only if you remove the hold.

## Stage 5 — Turret

New file `subsystems/turret/Turret.java`.

- `Motors.turret()` about the vertical axis, gravity **off** (yaw does not fall), limits **±π** from
  the cable wrap rather than from the mechanism.
- Tune `TurretConstants.KP` (preset near 3 V/rad).

Wire it: `m_blockBot.trackTurret(m_turret::getYawRadians);` and post `Turret/TargetRadians`
alongside `Turret/YawRadians`.

Done when: the turret holds wherever you point it, and a full sweep never drives the barrel into the
elevator — that clearance is proven at every angle, so if it clips, your angle is wrong.

## Gotchas

- **Disable cancels commands** and `finallyDo` runs. A joint that must stay put needs a hold command,
  not a voltage left behind by a cancelled one.
- **Bind in the `Robot` constructor.** Trigger bindings live on the scheduler's default button loop,
  which is polled every loop, so a binding you add at construction keeps working across mode changes.
- **`whileTrue` is the resume-after-disable binding.** It schedules when the composed condition rises
  and cancels when it falls, and the condition includes `teleop()` (mode and enabled), so holding a
  button through a disable re-schedules the command on enable.
- **Default commands are scheduled automatically** once you set one, and cannot require another
  subsystem.
- **Two commands requiring the same subsystem cannot run together** — the second preempts the first.
- **Re-assert voltage every loop.** `setVoltage` is not a latch.
- **Do not edit `Motor`, `Motors`, `BlockBot`, or the logging setup in `Robot`.** No stage needs it.

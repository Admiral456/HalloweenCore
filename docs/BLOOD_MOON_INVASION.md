# Krvavý měsíc — invaze

The Blood Moon is a normal random event selected alongside Soulstorm, Witching Hour and Cursed Harvest. It periodically spawns waves of special mobs around eligible players, applies a configured mob-kill fragment multiplier, and attempts to spawn a stronger Pumpkin Wraith captain during the final two minutes.

## Controls
- `random-events.duration-minutes`: base event duration.
- `random-events.surge-interval-seconds`: time between invasion waves.
- `random-events.invasion-mobs-per-surge`: 1–4 special mobs per wave.
- `random-events.max-event-mobs`: per-world cap for event-spawned mobs.

The captain is tagged as an event mob, drops the same special-mob rewards as a Pumpkin Wraith, and is removed together with remaining event mobs when the event ends or is stopped. The event does not unlock the Vampire finale or alter boss readiness.

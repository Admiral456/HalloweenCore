# Secret discoveries and easter eggs

## Admin setup
The default config reserves three IDs: `blood-altar`, `witch-den` and `forgotten-grave`. Travel to a suitable secret location and stand at the intended trigger point, then run:

```text
/halloween setsecret blood-altar
/halloween setsecret witch-den
/halloween setsecret forgotten-grave
```

Only IDs already defined under `secret-discoveries.locations` can be set. The command stores world and coordinates to the server config. Keep the clues descriptive without including the exact coordinates.

## Player experience
- `/halloween secrets` shows progress for configured secrets and hints to those not yet found.
- Entering a configured trigger radius grants the discovery once per player.
- Each discovery pays a configured fragment reward, plays a sound, shows title/particles and is saved in `plugins/HalloweenCore/data.yml`.
- The command never shows world coordinates to non-admins.
- Moving through the same trigger again cannot grant the same reward twice, even after a server restart.

## Config
Each location has `name`, `hint`, `configured`, `world`, `x/y/z`, `radius-blocks` and `reward-fragments`. The admin command fills the location and world values. Keep the global `secret-discoveries.enabled` false to disable all triggers.

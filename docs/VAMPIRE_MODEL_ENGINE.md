# Král upírů — ModelEngine integration

## Locked dimensions

The final model must preserve:

- minimum full height: **10 blocks**
- minimum full width including both wings: **8 blocks**
- wings are mandatory
- the minimum must remain true in the smallest animated pose

## Provider

HalloweenCore expects:

- provider: `MODEL_ENGINE`
- model id: `vampire_king`
- `required: true`
- `plugin-required: true`
- `ready: false` until the actual model is installed and tested

## MythicMobs hook

The dormant mob is `vampire-king`. A ModelEngine skill hook can be attached after the model is finalized. ModelEngine/MythicMobs examples use a model skill in the form of `model{model=...}`; do not enable the hook before the real model exists.

## Asset policy

When the model is made, commit together:

- model/blueprint data
- every texture used by the model
- animation data required by the model
- any required resource-pack metadata
- a preview/reference image

The boss must never ship with a fake placeholder model while `ready: true`.

#!/usr/bin/env python3
"""Validate the bundled BetonQuest 3.2 quest package and HalloweenCore bridge."""
from pathlib import Path
import yaml

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT / "betonquest/QuestPackages/warriorland_halloween_dialogues"
CONFIG = ROOT / "src/main/resources/config.yml"


def fail(message: str) -> None:
    raise SystemExit(f"BetonQuest package validation failed: {message}")


def load_yaml(path: Path):
    if not path.is_file():
        fail(f"missing file: {path.relative_to(ROOT)}")
    try:
        result = yaml.safe_load(path.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        fail(f"invalid YAML in {path.relative_to(ROOT)}: {exc}")
    if not isinstance(result, dict):
        fail(f"{path.relative_to(ROOT)} must contain a YAML mapping")
    return result


package = load_yaml(PACKAGE / "package.yml")
actions_doc = load_yaml(PACKAGE / "actions.yml")
conditions_doc = load_yaml(PACKAGE / "conditions.yml")
conversation_doc = load_yaml(PACKAGE / "conversations/farmar.yml")
config = load_yaml(CONFIG)

if not isinstance(package.get("npcs"), dict) or not isinstance(package.get("npc_conversations"), dict):
    fail("package.yml must define the npcs and npc_conversations mappings")
if package["npcs"] or package["npc_conversations"]:
    fail("the sample package must not guess a live Citizens NPC ID; bind it on the server")
items = package.get("items")
if not isinstance(items, dict) or items.get("wheat") != "simple WHEAT":
    fail('package.yml must define "wheat: \\"simple WHEAT\\""; BetonQuest items must be declared before they are referenced')

actions = actions_doc.get("actions")
conditions = conditions_doc.get("conditions")
conversations = conversation_doc.get("conversations")
if not all(isinstance(part, dict) for part in (actions, conditions, conversations)):
    fail("actions.yml, conditions.yml and conversation file must define their matching sections")
if "farmar" not in conversations:
    fail("missing sample conversation farmar")
conversation = conversations["farmar"]
npc_options = conversation.get("NPC_options", {})
player_options = conversation.get("player_options", {})
if not isinstance(npc_options, dict) or not isinstance(player_options, dict):
    fail("conversation must define NPC_options and player_options")


def ids(value):
    return [part.strip().lstrip("!") for part in str(value).split(",") if part.strip()]


for option_id in ids(conversation.get("first", "")):
    if option_id not in npc_options:
        fail(f"first points to missing NPC option {option_id}")

for options, opposite, label in (
    (npc_options, player_options, "NPC option"),
    (player_options, npc_options, "player option"),
):
    for option_id, option in options.items():
        for target in ids(option.get("pointers", "")):
            if target not in opposite:
                fail(f"{label} {option_id} points to missing option {target}")
        for condition_id in ids(option.get("conditions", "")):
            if condition_id not in conditions:
                fail(f"{label} {option_id} uses undefined condition {condition_id}")
        for action_id in ids(option.get("actions", "")):
            if action_id not in actions:
                fail(f"{label} {option_id} uses undefined action {action_id}")

for required in (
    "start_halloween_farmer_quest", "accept_farmer_quest",
    "take_farmer_wheat", "complete_halloween_farmer_quest",
    "finish_farmer_quest_sequence", "turn_in_farmer_quest",
):
    if required not in actions:
        fail(f"missing action {required}")
if "opsudo halloween quest start farmer_wheat" not in actions["start_halloween_farmer_quest"]:
    fail("acceptance must start the matching HalloweenCore quest")
if "opsudo halloween quest complete farmer_wheat" not in actions["complete_halloween_farmer_quest"]:
    fail("turn-in must call HalloweenCore's one-time completion/reward system")
if conditions.get("has_farmer_wheat") != "item wheat:10":
    fail("sample quest must verify ten real wheat items")
if actions.get("take_farmer_wheat") != "take wheat:10 abort":
    fail("turn-in must atomically take ten declared wheat items")
if actions.get("accept_farmer_quest") != "folder start_halloween_farmer_quest,add_farmer_started_tag":
    fail("acceptance must start the HalloweenCore quest and persist the BetonQuest tag")
if actions.get("finish_farmer_quest_sequence") != "folder take_farmer_wheat,complete_halloween_farmer_quest,add_farmer_completed_tag":
    fail("turn-in sequence must consume the wheat before completing and tagging the quest")
reward = config.get("quest-rewards", {}).get("quests", {}).get("farmer_wheat")
if not isinstance(reward, dict) or reward.get("fragments") != 5:
    fail("farmer_wheat must have the explicit sample reward of five fragments")
chances = reward.get("key-chances", {})
if any(float(chances.get(rarity, -1)) != 0.0 for rarity in ("common", "rare", "legendary")):
    fail("sample quest must not grant unrequested bonus keys")

print("BetonQuest validation passed: YAML valid, option/action/condition references resolve, ten wheat are checked, and HalloweenCore grants the sample five-fragment reward once.")

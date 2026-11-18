#!/usr/bin/env python3
"""PUBLIC, spoiler-free source for Developer Testing Mode content.

Everything here is noncanonical: fictional test characters, test quests and test places that exist only in
the developer testing world (the "Proving Grounds"). It must never mention story characters, quests or places;
tests/SpoilerGuardTest enforces that. Output goes to data/hollowatlas/<kind>/devtest/*.json.
    python3 tools/devtest_src/devtest_content.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "data", "hollowatlas")


def write(kind, obj):
    d = os.path.join(ROOT, kind, "devtest")
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, obj["id"] + ".json"), "w") as f:
        json.dump(obj, f, indent=2)


def A(t, **kw):
    kw["type"] = t
    return kw


def E(frm, to, activity, anchor=None, style="", radius=0, outdoors=False, route=None):
    e = {"from": frm, "to": to, "activity": activity}
    if anchor: e["anchor"] = anchor
    if style: e["style"] = style
    if radius: e["radius"] = radius
    if outdoors: e["outdoors"] = True
    if route: e["route"] = route
    return e


def N(text, choices=None, actions=None, nxt=None, speaker=None, variants=None):
    n = {"text": text}
    if choices: n["choices"] = choices
    if actions: n["actions"] = actions
    if nxt: n["next"] = nxt
    if speaker: n["speaker"] = speaker
    if variants: n["variants"] = variants
    return n


def CH(text, go="end", tag=None, when=None, actions=None, once=False, enabled_when=None, hint=None):
    c = {"text": text, "go": go}
    if tag: c["tag"] = tag
    if when: c["when"] = when
    if actions: c["actions"] = actions
    if once: c["once"] = True
    if enabled_when: c["enabled_when"] = enabled_when
    if hint: c["disabled_hint"] = hint
    return c


def stage(q, s):
    return {"quest_stage": {"quest": q, "stage": s}}


BYE = CH("Goodbye.", "end", tag="leave")
ASLEEP = N("(Asleep. This is the sleeping-resident test response.)", speaker="narrator")

# ----------------------------------------------------------------------------------------- residents
PATROL = ["dt_patrol_a", "dt_patrol_d", "dt_square_east", "dt_patrol_b", "dt_patrol_c"]
NPCS = [
    dict(id="dt_alda", name="Alda Quill", title="Test Clerk", skin="dt_alda", home="dt_house_a", work="dt_square_west", dialogue="dt_alda",
         traits=["helpful"], fallback_anchor="dt_square_west",
         schedule=[E(0, 7, "sleep", "dt_house_a.bed0"), E(7, 8, "eat", "dt_hall.table_a"),
                   E(8, 18, "work", "dt_square_west", "read", outdoors=True), E(18, 21, "social", "dt_house_a.door_out", radius=2, outdoors=True),
                   E(21, 24, "sleep", "dt_house_a.bed0")],
         rain=[E(8, 18, "work", "dt_hall.common_room", "read")],
         barks={"greet": ["Test quests are at my desk. Well, my spot by the square."], "rain": ["Rain test passed: I'm indoors."]}),
    dict(id="dt_bertram", name="Bertram Ode", title="Test Merchant", skin="dt_bertram", home="dt_house_b", work="dt_store", dialogue="dt_bertram",
         traits=["cheerful"], fallback_anchor="dt_store.counter_front",
         schedule=[E(0, 7, "sleep", "dt_house_b.bed0"), E(7, 8, "eat", "dt_house_b.table"),
                   E(8, 18, "work", "dt_store.work", "serve", 2), E(18, 21, "social", "dt_square_east", radius=3, outdoors=True),
                   E(21, 24, "sleep", "dt_house_b.bed0")],
         rain=[E(18, 21, "social", "dt_hall.common_room", radius=2)],
         barks={"greet": ["Everything's for sale. It's a test shop."], "night": ["Shop's closed. Schedules work!"]}),
    dict(id="dt_kade", name="Kade Halvard", title="Drill Sergeant", skin="dt_kade", home="dt_hall", work="dt_patrol_a", dialogue="dt_kade",
         held="minecraft:iron_sword", traits=["strict"], fallback_anchor="dt_square_east",
         schedule=[E(0, 6, "sleep", "dt_hall.bed_room_b"), E(6, 7, "eat", "dt_hall.table_b"),
                   E(7, 19, "patrol", route=PATROL, outdoors=True), E(19, 22, "social", "dt_hall.common_room", radius=3),
                   E(22, 24, "sleep", "dt_hall.bed_room_b")],
         barks={"greet": ["Feet apart. Guard up. Again."]}),
    dict(id="dt_juno", name="Juno Pike", title="Test Farmer", skin="dt_juno", home="dt_house_a", work="dt_garden", dialogue="dt_juno",
         held="minecraft:iron_hoe", traits=["calm"], fallback_anchor="dt_house_a.door_out",
         schedule=[E(0, 6, "sleep", "dt_house_a.bed1"), E(6, 12, "work", "dt_garden", "tend", 7, outdoors=True),
                   E(12, 13, "eat", "dt_house_a.table"), E(13, 17, "work", "dt_farm", "tend", 7, outdoors=True),
                   E(17, 21, "social", "dt_house_a.door_out", radius=2, outdoors=True), E(21, 24, "sleep", "dt_house_a.bed1")],
         rain=[E(6, 17, "work", "dt_workshop.counter_front", "pack", 2)],
         variants={"moved": [E(0, 6, "sleep", "dt_house_a.bed1"), E(6, 17, "work", "dt_pond", "fish", outdoors=True),
                             E(17, 21, "social", "dt_house_a.door_out", radius=2, outdoors=True), E(21, 24, "sleep", "dt_house_a.bed1")]},
         barks={"greet": ["Rows are straight today."], "rain": ["Rain routine: I go indoors."]}),
    dict(id="dt_remy", name="Remy Tallow", title="Test Innkeeper", skin="dt_remy", home="dt_hall", work="dt_hall", dialogue="dt_remy",
         traits=["warm"], fallback_anchor="dt_hall.counter_front",
         schedule=[E(0, 6, "sleep", "dt_hall.bed_room_c"), E(6, 23, "work", "dt_hall.work", "serve", 3), E(23, 24, "sleep", "dt_hall.bed_room_c")],
         barks={"greet": ["The hearth works. So do the stairs, mostly."]}),
]
for n in NPCS:
    write("npcs", n)

write("ambient", dict(id="dt_housemates", speakers=["dt_alda", "dt_juno"], **{"from": 17, "to": 21}, radius=6, cooldown_minutes=5, lines=[
    ["dt_alda", "Ambient talk test, line one."], ["dt_juno", "Line two. Do you hear us?"], ["dt_alda", "If so, it works."]]))
write("ambient", dict(id="dt_square_talk", speakers=["dt_bertram", "dt_kade"], **{"from": 18, "to": 21}, radius=10, cooldown_minutes=5, lines=[
    ["dt_kade", "Sales today?"], ["dt_bertram", "All test sales."], ["dt_kade", "Good."]]))

# ----------------------------------------------------------------------------------------- dialogue
write("dialogue", dict(id="dt_alda", npc="dt_alda", entries=[
    dict(when=stage("dtq_errand", "return"), node="errand_done"),
    dict(when=stage("dtq_branch", "choose"), node="branch"),
    dict(node="menu")], nodes={
    "asleep": ASLEEP,
    "menu": N("Hello, tester. Pick a test quest, or ask me about this place.",
              [CH("Start: Errand Chain", "end", tag="quest", when={"quest_not_started": "dtq_errand"}, actions=[A("start_quest", quest="dtq_errand")]),
               CH("Start: Branching Choice", "end", tag="quest", when={"quest_not_started": "dtq_branch"}, actions=[A("start_quest", quest="dtq_branch")]),
               CH("Start: Case Board", "end", tag="quest", when={"quest_not_started": "dtq_case"}, actions=[A("start_quest", quest="dtq_case")]),
               CH("Start: Arena Trial", "end", tag="quest", when={"quest_not_started": "dtq_arena"}, actions=[A("start_quest", quest="dtq_arena")]),
               CH("Start: Cooking", "end", tag="quest", when={"quest_not_started": "dtq_kitchen"}, actions=[A("start_quest", quest="dtq_kitchen")]),
               CH("Start: Lever Puzzle", "end", tag="quest", when={"quest_not_started": "dtq_puzzle"}, actions=[A("start_quest", quest="dtq_puzzle")]),
               CH("What is this place?", "place", tag="ask"),
               CH("Test a once-only choice.", "once", tag="ask", once=True),
               CH("Test a locked choice (needs 1000 coins).", "unlocked", enabled_when={"coins_min": 1000}, hint="needs 1000 coins (Developer Menu > Items > +1000 coins)."),
               BYE]),
    "place": N("The Proving Grounds: a test world. Nothing here belongs to any story. Break things. Report them.", [CH("Back.", "menu"), BYE]),
    "unlocked": N("Locked choice test passed: it unlocked because you have 1000 or more coins.", [CH("Back.", "menu"), BYE]),
    "once": N("This choice disappears after you pick it once. If you see it again, that's a bug.", [CH("Back.", "menu"), BYE]),
    "errand_done": N("You did every step of the errand chain. Reward incoming.",
                     [CH("Thanks.", "end", actions=[A("quest_event", event="talk", target="dt_errand_done")])]),
    "branch": N("Branch test. Choose one; the result should persist after saving and reloading.",
                [CH("Option A: build the footbridge over the brook.", "branch_a",
                    actions=[A("choice", quest="dtq_branch", key="path", value="a"), A("world_flag", flag="hollowatlas:dt_gap_bridged"),
                             A("quest_event", event="talk", target="dt_branch_choice")]),
                 CH("Option B: move Juno's work to the pond.", "branch_b",
                    actions=[A("choice", quest="dtq_branch", key="path", value="b"), A("set_variant", npc="dt_juno", variant="moved"),
                             A("quest_event", event="talk", target="dt_branch_choice")])]),
    "branch_a": N("The footbridge should now exist north of the square. Walk across it.", [BYE]),
    "branch_b": N("Juno's schedule changed. Find her at the pond during the day.", [BYE]),
}))
write("dialogue", dict(id="dt_bertram", npc="dt_bertram", entries=[dict(node="main")], nodes={
    "asleep": ASLEEP,
    "main": N("Test shop open.", variants=[dict(when={"time": {"from": 18, "to": 24}}, text="Evening variant of my greeting (time condition test).")],
              choices=[CH("Trade.", "end", tag="trade", actions=[A("open_shop", shop="dt_shop")]),
                       CH("Heard any rumors?", "rumor", tag="ask", when={"quest_active": "dtq_case"}),
                       CH("Raise our relationship (+5).", "main", actions=[A("relationship", delta=5)]),
                       CH("Relationship-gated line.", "friend", enabled_when={"relationship": {"min": 10}}, hint="Needs relationship 10 with Bertram."),
                       BYE]),
    "rumor": N("A rumor: the bucket was never real. Unconfirmed, of course.", [BYE],
               actions=[A("evidence", id="dt_ev_rumor")]),
    "friend": N("Relationship check passed.", [BYE]),
}))
write("dialogue", dict(id="dt_kade", npc="dt_kade", entries=[dict(node="main")], nodes={
    "asleep": ASLEEP,
    "main": N("Drill time.", variants=[dict(when={"weather": "rain"}, text="Rain variant of my greeting (weather condition test).")],
              choices=[CH("Teach me Windstep.", "learn", when={"not": {"ability": "windstep"}}),
                       CH("Explain combat controls.", "controls", tag="ask"), BYE]),
    "learn": N("Weight forward. Push. Done. That called the real unlock code.", [BYE], actions=[A("unlock_ability", ability="windstep")]),
    "controls": N("Left click light. R heavy. Left Alt dodge. Hold V to guard; tap it as a hit lands to parry. Use the arena for practice.", [BYE]),
}))
write("dialogue", dict(id="dt_juno", npc="dt_juno", entries=[
    dict(when=stage("dtq_branch", "b_visit"), node="moved"),
    dict(when={"quest_active": "dtq_case"}, node="testimony"),
    dict(node="main")], nodes={
    "asleep": ASLEEP,
    "main": N("Just tending test crops.", [BYE]),
    "testimony": N("Testimony: I saw someone carry a bucket toward the cart. Could've been anyone.", [BYE],
                   actions=[A("evidence", id="dt_ev_testimony"), A("quest_event", event="talk", target="dt_case_testimony")]),
    "moved": N("My routine moved to the pond. Variant test passed.", [BYE],
               actions=[A("quest_event", event="talk", target="dt_branch_juno")]),
}))
write("dialogue", dict(id="dt_remy", npc="dt_remy", entries=[dict(when=stage("dtq_errand", "talk"), node="errand"), dict(node="main")], nodes={
    "asleep": ASLEEP,
    "main": N("Hall's open. Hearth's warm. Rooms upstairs.", [CH("Trade.", "end", tag="trade", actions=[A("open_shop", shop="dt_shop")]), BYE]),
    "errand": N("Alda sent you? Errand step one complete. Next: read the notice board.", [BYE],
                actions=[A("quest_event", event="talk", target="dt_errand_talk")]),
}))
write("dialogue", dict(id="dt_notice", npc=None, entries=[dict(node="board")], nodes={
    "board": N("TEST NOTICE BOARD\n- Reading this counts for the Errand Chain test.\n- Report bugs with what you did and what happened.",
               [CH("(Done reading.)", "end", tag="leave")], speaker="narrator")}))

# ----------------------------------------------------------------------------------------- quests
QUESTS = [
    dict(id="dtq_errand", title="Test: Errand Chain", category="test", giver="dt_alda", abandonable=True,
         summary="Exercises talk, inspect, reach and gather objectives in sequence.",
         stages=[dict(id="talk", text="Talk to Remy in the Test Hall.", objectives=[dict(id="t", type="talk", target="dt_errand_talk", text="Talk to Remy", marker="dt_hall.counter_front")], next="board"),
                 dict(id="board", text="Read the test notice board.", objectives=[dict(id="b", type="inspect", target="dt_board", text="Read the board", marker="dt_board.read")], next="reach"),
                 dict(id="reach", text="Walk to the pond.", objectives=[dict(id="r", type="reach", target="dt_pond", radius=6, text="Reach the pond", marker="dt_pond")], next="gather"),
                 dict(id="gather", text="Pick two yarrow at the west herb spots.", objectives=[dict(id="g", type="gather", target="hollowatlas:yarrow", count=2, text="Yarrow", marker="dt_herb_1")], next="return"),
                 dict(id="return", text="Report to Alda.", objectives=[dict(id="a", type="talk", target="dt_errand_done", text="Talk to Alda", marker="dt_square_west")], next="complete")],
         rewards=[A("coins", amount=20)], completion_text="Errand chain test complete."),
    dict(id="dtq_branch", title="Test: Branching Choice", category="test", giver="dt_alda", abandonable=True,
         summary="Exercises a dialogue branch that changes the world or a resident's routine, persistently.",
         stages=[dict(id="choose", text="Ask Alda for the branch test and pick an option.", objectives=[dict(id="c", type="talk", target="dt_branch_choice", text="Choose A or B", marker="dt_square_west")],
                      next=[dict(when={"choice": {"quest": "dtq_branch", "key": "path", "value": "a"}}, stage="a_cross"),
                            dict(when={"choice": {"quest": "dtq_branch", "key": "path", "value": "b"}}, stage="b_visit")]),
                 dict(id="a_cross", text="Option A: cross the new footbridge to the far bank.", objectives=[dict(id="x", type="reach", target="dt_gap_south", radius=4, text="Cross the brook", marker="dt_gap_south")], next="complete"),
                 dict(id="b_visit", text="Option B: find Juno at the pond (daytime) and talk to her.", objectives=[dict(id="v", type="talk", target="dt_branch_juno", text="Talk to Juno at the pond", marker="dt_pond")], next="complete")],
         rewards=[A("coins", amount=10)], completion_text="Branch test complete. Reload the world: the result should still be there."),
    dict(id="dtq_case", title="Test: Case Board", category="test", giver="dt_alda", abandonable=True,
         summary="Exercises evidence of each kind and theories on the case board.",
         stages=[dict(id="look", text="Collect a fact (the cart), a testimony (Juno) and a rumor (Bertram).",
                      objectives=[dict(id="f", type="inspect", target="dt_cart_clue", text="Inspect the cart", marker="dt_cart.clue"),
                                  dict(id="t", type="talk", target="dt_case_testimony", text="Ask Juno"),
                                  dict(id="r", type="evidence", target="dt_ev_rumor", text="Hear Bertram's rumor")], next="think"),
                 dict(id="think", text="Open the Case Board (J) and pick any theory. Then try changing it.",
                      objectives=[dict(id="h", type="hypothesis", target="dt_case", text="Pick a theory")], next="complete")],
         rewards=[A("coins", amount=10)], completion_text="Case board test complete."),
    dict(id="dtq_arena", title="Test: Arena Trial", category="test", giver="dt_alda", abandonable=True,
         summary="Exercises kill objectives with one melee and one ranged enemy.",
         stages=[dict(id="fight", text="Enter the test arena (south). One melee brigand and one slinger appear.",
                      objectives=[dict(id="m", type="kill", target="hollowatlas:brigand", text="Defeat the brigand", marker="dt_arena.center"),
                                  dict(id="s", type="kill", target="hollowatlas:slinger", text="Defeat the slinger")], next="complete")],
         rewards=[A("give_item", item="hollowatlas:trail_tonic", count=2), A("coins", amount=10)], completion_text="Arena test complete."),
    dict(id="dtq_kitchen", title="Test: Cooking", category="test", giver="dt_alda", abandonable=True,
         summary="Exercises gathering nodes, buying, and station crafting.",
         stages=[dict(id="cook", text="Gather a carrot and a potato from the test fields, buy mutton from Bertram, then cook Hearth Stew at the Test Hall hearth.",
                      objectives=[dict(id="c", type="craft", target="hearth_stew", text="Cook Hearth Stew", marker="dt_hall.hearth")], next="complete")],
         rewards=[A("coins", amount=10)], completion_text="Cooking test complete."),
    dict(id="dtq_puzzle", title="Test: Lever Puzzle", category="test", giver="dt_alda", abandonable=True,
         summary="Exercises a lever puzzle, a world change and a one-time cache.",
         stages=[dict(id="solve", text="Pull the levers in the order on the sign (west of the square).", objectives=[dict(id="p", type="solve", target="dt_levers", text="Solve the lever puzzle", marker="dt_levers.stand")], next="loot"),
                 dict(id="loot", text="Open the hatch cache.", objectives=[dict(id="l", type="inspect", target="dt_lever_cache", text="Take the cache")], next="complete")],
         rewards=[A("coins", amount=10)], completion_text="Puzzle test complete."),
]
for q in QUESTS:
    write("quests", q)

# ----------------------------------------------------------------------------------------- interactions etc.
write("interactions", dict(id="dt_interactions", interactions=[
    dict(id="dt_board", anchor="dt_board.sign", type="read", text="", actions=[A("open_dialogue", tree="dt_notice", node="board")]),
    dict(id="dt_rest", anchor="dt_hall.bed_room_guest", type="rest", text="Rest test: you sleep until morning (if it's night)."),
    dict(id="dt_hearth", anchor="dt_hall.hearth_block", type="station", station="hearth", text="Test hearth."),
    dict(id="dt_cart_clue", anchor="dt_cart.clue", type="inspect", once=True, evidence="dt_ev_cart",
         text="FACT TEST: an empty bucket hook on the cart. Inspecting again must not duplicate the evidence."),
    dict(id="dt_lever_red", anchor="dt_levers.lever_red", type="puzzle", puzzle="dt_levers", symbol="red", text="Red lever."),
    dict(id="dt_lever_green", anchor="dt_levers.lever_green", type="puzzle", puzzle="dt_levers", symbol="green", text="Green lever."),
    dict(id="dt_lever_blue", anchor="dt_levers.lever_blue", type="puzzle", puzzle="dt_levers", symbol="blue", text="Blue lever."),
    dict(id="dt_lever_cache", anchor="dt_levers.cache", type="cache", when={"world_flag": "hollowatlas:dt_levers_solved"}, locked_text="Closed hatch.",
         text="Test cache: one-time loot. Opening it again should say it's empty.", loot=[dict(item="hollowatlas:old_coin", min=2, max=2), dict(item="hollowatlas:trail_tonic")]),
] + [dict(id=f"dt_herb_{i}", anchor=f"dt_herb_{i}", type="gather", text="Gathered yarrow (test node).", loot=[dict(item="hollowatlas:yarrow", min=1, max=1)],
          regrow_minutes=2, depleted_state="minecraft:air", ripe_state="minecraft:azure_bluet") for i in range(1, 4)]
  + [dict(id=f"dt_daisy_{i}", anchor=f"dt_daisy_{i}", type="gather", text="Gathered feverfew (test node).", loot=[dict(item="hollowatlas:feverfew", min=1, max=1)],
          regrow_minutes=2, depleted_state="minecraft:air", ripe_state="minecraft:oxeye_daisy") for i in range(1, 3)]
  + [dict(id=f"dt_berry_{i}", anchor=f"dt_berry_{i}", type="gather", text="Gathered berries (test node).", loot=[dict(item="minecraft:sweet_berries", min=2, max=2)],
          regrow_minutes=2, depleted_state="minecraft:sweet_berry_bush[age=1]", ripe_state="minecraft:sweet_berry_bush[age=3]") for i in range(1, 3)]
  + [dict(id="dt_carrots_1", anchor="dt_carrots_1", type="gather", text="Pulled a carrot (test node).", loot=[dict(item="minecraft:carrot", min=1, max=1)],
          regrow_minutes=2, depleted_state="minecraft:carrots[age=0]", ripe_state="minecraft:carrots[age=7]"),
     dict(id="dt_potatoes_1", anchor="dt_potatoes_1", type="gather", text="Dug a potato (test node).", loot=[dict(item="minecraft:potato", min=1, max=1)],
          regrow_minutes=2, depleted_state="minecraft:potatoes[age=0]", ripe_state="minecraft:potatoes[age=7]")],
    puzzles=[dict(id="dt_levers", sequence=["red", "green", "blue"], solved_flag="hollowatlas:dt_levers_solved",
                  reset_text="Wrong order: levers reset (this is the failure path).", solved_text="Correct order: the hatch opens.")]))

write("cases", dict(id="dt_case", title="Test Case: The Missing Bucket", summary="Noncanonical case for testing the board.",
                    visible_when={"any": [{"quest_active": "dtq_case"}, {"quest_done": "dtq_case"}]},
                    evidence=[dict(id="dt_ev_cart", title="Empty hook", text="The cart's bucket hook is empty.", kind="fact", source="Test cart"),
                              dict(id="dt_ev_testimony", title="Juno's account", text="Juno saw someone carry a bucket toward the cart.", kind="testimony", source="Juno"),
                              dict(id="dt_ev_rumor", title="Bertram's rumor", text="The bucket never existed.", kind="rumor", source="Bertram")],
                    hypotheses=[dict(id="taken", text="Someone took it", needs=["dt_ev_cart", "dt_ev_testimony"], reaction="Test theory recorded."),
                                dict(id="never", text="It never existed", needs=["dt_ev_rumor"], reaction="Test theory recorded (rumor-based)."),
                                dict(id="all", text="Needs all three clues", needs=["dt_ev_cart", "dt_ev_testimony", "dt_ev_rumor"], reaction="Test theory recorded.")]))

write("shops", dict(id="dt_shop", title="Test Store", npc="dt_bertram", anchor="dt_store.counter_front", restock_days=1,
                    sells=[dict(item="minecraft:bread", price=2, stock=5), dict(item="minecraft:mutton", price=2, stock=5),
                           dict(item="hollowatlas:honey_bun", price=3, stock=3), dict(item="minecraft:shield", price=20, stock=1),
                           dict(item="minecraft:leather_chestplate", price=12, stock=1), dict(item="minecraft:apple", price=1, stock=1)],
                    buys={"hollowatlas:yarrow": 2, "hollowatlas:feverfew": 2, "minecraft:sweet_berries": 1, "minecraft:carrot": 1,
                          "minecraft:potato": 1, "hollowatlas:old_coin": 6},
                    buy_back_rate=0.5))

write("encounters", dict(id="dt_arena_trial", anchor="dt_arena.center", trigger_radius=12, daily=True, when={"quest_active": "dtq_arena"},
                         spawns=[dict(kind="brigand", dx=-3, dz=-3), dict(kind="slinger", dx=4, dz=-5)],
                         start_text="Arena test: two enemies enter."))

write("world_changes", dict(id="dt_changes", changes=[
    dict(id="dt_footbridge", flag="hollowatlas:dt_gap_bridged",
         blocks=[dict(anchor="dt_gap", dx=dx, dy=0, dz=dz, state="minecraft:spruce_planks") for dz in range(-7, 8) for dx in (-1, 0, 1)],
         otherwise=[dict(anchor="dt_gap", dx=dx, dy=0, dz=dz, state="minecraft:air") for dz in range(-2, 3) for dx in (-1, 0, 1)]),
    dict(id="dt_hatch", flag="hollowatlas:dt_levers_solved",
         blocks=[dict(anchor="dt_levers.hatch", state="minecraft:iron_trapdoor[facing=north,half=top,open=true,powered=false,waterlogged=false]")],
         otherwise=[dict(anchor="dt_levers.hatch", state="minecraft:iron_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]")]),
]))

write("sites", dict(id="dt_sites", sites=[
    dict(id="dt_square", name="Test Square", anchor="dt_plaza", radius=15),
    dict(id="dt_arena_site", name="Test Arena", anchor="dt_arena.center", radius=12),
    dict(id="dt_pond_site", name="Test Pond", anchor="dt_pond", radius=10),
    dict(id="dt_puzzle_site", name="Test Puzzle", anchor="dt_levers.stand", radius=8),
    dict(id="dt_brook_site", name="Test Brook", anchor="dt_gap_north", radius=8),
]))

print("devtest content written:", len(NPCS), "residents,", len(QUESTS), "quests")

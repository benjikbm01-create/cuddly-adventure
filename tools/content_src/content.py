#!/usr/bin/env python3
"""Authoring source for Hollow Atlas v0.1 game content (SPOILERS for the opening).
Generates JSON under src/main/resources/data/hollowatlas/<kind>/.  Run after edits:
    python3 tools/content_src/content.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "data", "hollowatlas")


def write(kind, obj):
    d = os.path.join(ROOT, kind)
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, obj["id"] + ".json"), "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)


def A(t, **kw):
    kw["type"] = t
    return kw


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
ASLEEP = N("(Fast asleep. Best not to wake them.)", speaker="narrator")

# =====================================================================================  QUESTS

QUESTS = [
    dict(id="m01", title="A Room for the Rain", category="main", giver="nella",
         summary="You came in off the west road with wet boots and no plans beyond a dry bed.",
         stages=[
             dict(id="arrive", text="The road east should lead to a village. Somewhere with a roof.",
                  hint="Follow the road east from the milestone.",
                  objectives=[dict(id="reach", type="reach", target="plaza", radius=16, text="Reach the village", marker="plaza")],
                  next="inn"),
             dict(id="inn", text="Brindlecross. The building with lanterns and smoke looks like an inn.",
                  hint="The inn is the large timbered house on the west side of the square.",
                  objectives=[dict(id="talk", type="talk", target="nella_room", text="Ask about a room at the inn", marker="inn.counter_front")],
                  next="settle"),
             dict(id="settle", text="A room, a key, and advice you didn't ask for. The notice board by the hall is apparently where work turns up.",
                  hint="The notice board stands near the stone hall north of the square.",
                  objectives=[dict(id="board", type="inspect", target="notice_board", text="Read the notice board", marker="notice_board.read"),
                              dict(id="rest", type="inspect", target="inn_rest", text="Rest in your room", optional=True, marker="inn.room_guest")],
                  next="complete"),
         ],
         rewards=[A("give_item", item="hollowatlas:hearth_stew", count=2), A("set_flag", flag="visited_brindlecross")],
         completion_text="You have a room at the Rain & Lantern for as long as you need it."),

    dict(id="m02", title="The Orchard Ledger", category="main", giver="tobin",
         prereq={"flag": "met_nella"},
         summary="Tobin Ashby's orchard pump has given up, and the summer is getting on.",
         stages=[
             dict(id="look", text="Tobin asked you to take a look at the pump in the orchard before he loses another day hauling buckets.",
                  hint="The pump stands among the orchard rows north of the village.",
                  objectives=[dict(id="pump", type="inspect", target="pump_look", text="Examine the pump", marker="orchard_pump.body"),
                              dict(id="trough", type="inspect", target="pump_trough", text="Check the trough", marker="orchard_pump.trough"),
                              dict(id="ledger", type="inspect", target="orchard_ledger", text="Read the orchard ledger at the farmhouse", marker="farmhouse.table")],
                  next="parts"),
             dict(id="parts", text="The pin is sheared and the valve leather has perished. A smith can make a pin. Leather is another matter.",
                  hint="Garrick at the smithy can make a pin. Ada at the general store keeps leather.",
                  objectives=[dict(id="pin", type="gather", target="hollowatlas:pump_pin", text="Get a new pump pin", marker="smithy.counter_front"),
                              dict(id="leather", type="gather", target="hollowatlas:valve_leather", text="Find valve leather")],
                  next="repair"),
             dict(id="repair", text="You have what the pump needs.",
                  objectives=[dict(id="fix", type="inspect", target="pump_fix", text="Repair the pump", marker="orchard_pump.body")],
                  next="report"),
             dict(id="report", text="Water's running again. Tobin will want to hear it.",
                  objectives=[dict(id="tell", type="talk", target="tobin_pump_done", text="Tell Tobin", marker="farmhouse.door_out")],
                  next="complete"),
         ],
         rewards=[A("coins", amount=20), A("give_item", item="minecraft:apple", count=6), A("relationship", npc="tobin", delta=10),
                  A("relationship", npc="wenna", delta=5)],
         completion_text="The orchard pump runs again. Tobin seemed lighter for it."),

    dict(id="m03", title="A Parcel Bound East", category="main", giver="corin",
         prereq={"quest_done": "m01"},
         summary="Corin Bell has a parcel that needs to travel east faster than he does.",
         stages=[
             dict(id="relay", text="Corin's parcel is meant for a clinic in the mountains. First it has to be logged at the East Road Relay.",
                  hint="Follow the east road out of the village. The relay is a long way out, past the watchtower and the woods.",
                  objectives=[dict(id="log", type="talk", target="jory_parcel", text="Have the parcel logged at the relay", marker="relay.desk")],
                  next="complete"),
         ],
         rewards=[A("coins", amount=10), A("start_quest", quest="m04")],
         completion_text="The parcel is logged for the eastern route."),

    dict(id="m04", title="The Wrong Seal", category="main", giver="jory",
         summary="A crate at the relay has been waiting on the eastern bridge for weeks.",
         stages=[
             dict(id="look", text="Jory mentioned a crate bound east that has been waiting since the bridge went out. Something about it bothers you.",
                  hint="The manifest is on the lectern; the crates are stacked on the east side of the relay.",
                  objectives=[dict(id="manifest", type="inspect", target="relay_manifest", text="Read the manifest", marker="relay.desk"),
                              dict(id="crate", type="inspect", target="relay_crate", text="Look over the crate", marker="relay.crates"),
                              dict(id="seal", type="inspect", target="relay_seal", text="Look at the seal on the strongbox", marker="relay.desk")],
                  next="think"),
             dict(id="think", text="Three things that don't quite agree. Put them side by side.",
                  hint="Open the journal (J) and look at the Case Board.",
                  objectives=[dict(id="theory", type="hypothesis", target="relay_case", text="Form a theory on the Case Board")],
                  next="decide"),
             dict(id="decide", text="You could tell Jory what you noticed, or keep it to yourself for now.",
                  objectives=[dict(id="choice", type="talk", target="seal_decided", text="Decide what to do about it", marker="relay.desk")],
                  next="bridge"),
             dict(id="bridge", text="Whatever the crate is, it isn't going anywhere, and neither is your parcel. The bridgewarden will know when the Eastfall crossing reopens.",
                  hint="The Bridgewarden's hut is at the very end of the east road.",
                  objectives=[dict(id="lom", type="talk", target="lom_bridge", text="Ask the bridgewarden about the crossing", marker="east_gate")],
                  next="complete"),
         ],
         rewards=[A("coins", amount=10), A("set_flag", flag="opening_complete")],
         completion_text="The Eastfall crossing is still out. The road east will have to wait."),

    # ------------------------------------------------------------------- side
    dict(id="sq01", title="The Blue Ribbon Stall", category="side", giver="sabine",
         summary="Sabine's prize ribbon is gone, and the empty market stall has two claimants.",
         stages=[
             dict(id="look", text="Sabine says her blue festival ribbon vanished from a hook by her workshop door. She has her suspicions.",
                  hint="Look around the Weaver's Cottage door, and the bakery. People in the square might have seen something.",
                  objectives=[dict(id="hook", type="inspect", target="ribbon_hook", text="Look at the weaver's door", marker="weaver_house.door_out"),
                              dict(id="thread", type="inspect", target="ribbon_thread", text="Check the bakery", marker="bakery.door_out"),
                              dict(id="ask", type="talk", target="ribbon_witness", text="Ask around the square")],
                  next="think"),
             dict(id="think", text="Thread, tracks and a boy who saw something. Time to decide what you believe.",
                  hint="Open the Case Board in your journal (J).",
                  objectives=[dict(id="theory", type="hypothesis", target="ribbon_case", text="Pick a theory on the Case Board")],
                  next="find"),
             dict(id="find", text="Theories are cheap. The ribbon is somewhere.",
                  hint="Foxes keep dens in the woods south-east, near the heron pool.",
                  objectives=[dict(id="ribbon", type="gather", target="hollowatlas:blue_ribbon", text="Find the ribbon", marker="fox_den")],
                  next="return"),
             dict(id="return", text="Muddy, chewed at one end, but whole.",
                  objectives=[dict(id="give", type="talk", target="ribbon_returned", text="Return the ribbon to Sabine", marker="weaver_house.door_out")],
                  next="stall"),
             dict(id="stall", text="Ada keeps the market roll. She's asked for your view on who should have the empty stall.",
                  objectives=[dict(id="decide", type="talk", target="stall_decided", text="Give Ada your recommendation", marker="store.counter_front")],
                  next="complete"),
         ],
         rewards=[A("coins", amount=15), A("relationship", npc="ada", delta=5)],
         completion_text="The second market stall is open again."),

    dict(id="sq02", title="The Last Delivery", category="side", giver="elsie",
         summary="Elsie Varrow walked the Westreach routes for forty years. She wants the old one written down before nobody remembers it.",
         stages=[
             dict(id="walk", text="Elsie's route, from her notes: the west milestone, the lone oak on the north meadow, the top of Lark Hill, and the old cart track through Mossgate Wood.",
                  hint="Your Atlas (M) charts each place once you've stood there.",
                  objectives=[dict(id="mile", type="reach", target="milestone_west.sign", radius=6, text="The west milestone", marker="milestone_west.sign"),
                              dict(id="oak", type="reach", target="lone_oak.stand", radius=8, text="The lone oak", marker="lone_oak.stand"),
                              dict(id="hill", type="reach", target="overlook_cairn.view", radius=6, text="The top of Lark Hill", marker="overlook_cairn.view"),
                              dict(id="wagon", type="reach", target="wagon", radius=10, text="The old cart track in Mossgate Wood", marker="wagon")],
                  next="clear"),
             dict(id="clear", text="Someone has made a camp of Elsie's old cart track. They didn't look like they wanted visitors.",
                  hint="Deal with the brigands at the wagon, or come back better prepared.",
                  objectives=[dict(id="brigands", type="flag", target="hollowatlas:encounter_cleared:wagon_ambush", text="Clear the cart track")],
                  next="report"),
             dict(id="report", text="The route is walked. Elsie will want every detail.",
                  objectives=[dict(id="tell", type="talk", target="elsie_route_done", text="Report to Elsie", marker="old_cottage.door_out")],
                  next="complete"),
         ],
         rewards=[A("coins", amount=20), A("give_item", item="hollowatlas:courier_charm", count=1), A("relationship", npc="elsie", delta=10)],
         completion_text="Elsie's route is written down. She made you promise to keep the notes dry."),

    dict(id="sq03", title="Hester's Remedy", category="side", giver="hester", on_start=[A("set_flag", flag="recipe_remedy")],
         summary="Elsie's hip aches in the damp. Hester knows a remedy, if someone fetches the herbs.",
         stages=[
             dict(id="gather", text="Hester needs three sprigs of yarrow from the meadow south-west of the village and two heads of feverfew from the riverbank.",
                  hint="Yarrow grows in small white clusters; feverfew looks like a daisy. Look for them, then pick them.",
                  objectives=[dict(id="yarrow", type="gather", target="hollowatlas:yarrow", count=3, text="Yarrow", marker="yarrow_1"),
                              dict(id="feverfew", type="gather", target="hollowatlas:feverfew", count=2, text="Feverfew", marker="feverfew_1")],
                  next="cook"),
             dict(id="cook", text="Hester taught you the remedy. It wants a slow fire, and the inn hearth is the warmest in the village.",
                  hint="Use the hearth in the Rain & Lantern's common room.",
                  objectives=[dict(id="cook", type="craft", target="herbal_remedy", text="Prepare the remedy at the inn hearth", marker="inn.hearth")],
                  next="deliver"),
             dict(id="deliver", text="It smells terrible, which Hester says is a good sign.",
                  objectives=[dict(id="give", type="talk", target="remedy_given", text="Bring the remedy to Elsie", marker="old_cottage.door_out")],
                  next="complete"),
         ],
         rewards=[A("give_item", item="hollowatlas:trail_tonic", count=2), A("set_flag", flag="recipe_tonic"),
                  A("relationship", npc="hester", delta=8), A("relationship", npc="elsie", delta=5)],
         completion_text="Elsie's hip is better. Hester taught you how to make a trail tonic."),

    dict(id="sq04", title="Bread for the Watch", category="side", giver="maren",
         prereq={"quest_done": "m01"},
         summary="Maren sends a basket of buns up the east road every week. This week her legs have opinions about it.",
         stages=[
             dict(id="deliver", text="Take Maren's basket to Lom Perrin, the bridgewarden at the end of the east road.",
                  hint="Follow the east road past the watchtower and the relay to the bridge barrier.",
                  objectives=[dict(id="lom", type="talk", target="buns_delivered", text="Deliver the buns to Lom", marker="east_gate")],
                  next="back"),
             dict(id="back", text="Lom sent something back with you. Maren will want to know he ate.",
                  objectives=[dict(id="maren", type="talk", target="buns_reported", text="Tell Maren", marker="bakery.door_out")],
                  next="complete"),
         ],
         rewards=[A("coins", amount=8), A("give_item", item="hollowatlas:honey_bun", count=2), A("relationship", npc="maren", delta=6),
                  A("relationship", npc="lom", delta=4)],
         completion_text="Lom has his buns. Maren pretended not to care how he was."),

]

# =====================================================================================  DIALOGUE

D = {}

D["nella"] = dict(id="nella", npc="nella", entries=[
    dict(when=stage("m01", "inn"), node="room"),
    dict(when={"quest_active": "sq03", "time": {"from": 0, "to": 24}}, node="main"),
    dict(node="main"),
], nodes={
    "asleep": ASLEEP,
    "room": N("Shut the door, you're letting the weather in. You've the look of someone who walked a long way to stand in my doorway dripping.",
              [CH("I need a room.", "room2", tag="quest"),
               CH("Is it always this wet here?", "weather", tag="ask")]),
    "weather": N("Only in spring, summer and autumn. Winter's mostly ice. You'll want a room, then.", [CH("I will.", "room2", tag="quest")]),
    "room2": N("First one up the stairs, on the left. Bed's dry, which puts it ahead of you. Where've you come from, then?",
               [CH("From the coast, a long way west.", "room3", actions=[A("set_flag", flag="background_coast")]),
                CH("From the hill farms. I've worked a lot of harvests.", "room3", actions=[A("set_flag", flag="background_farms")]),
                CH("Here and there. I don't stay anywhere long.", "room3", actions=[A("set_flag", flag="background_road")])]),
    "room3": N("Well, you're here now. Here's the key. Breakfast is whatever Maren didn't sell yesterday, supper's stew. Keep to the roads after dark, Ilse gets twitchy about strangers in hedges.",
               [CH("Is there work in the village?", "work", tag="ask")],
               actions=[A("give_item", item="hollowatlas:room_key"), A("set_flag", flag="met_nella"), A("checkpoint", anchor="inn.common_room"),
                        A("quest_event", event="talk", target="nella_room"), A("relationship", delta=2)]),
    "work": N("Work finds people here, mostly by the notice board outside the Hall. And if Tobin Ashby comes in looking like a wet sheep, it's his pump again. You could do worse than help him.",
              [CH("Thanks, Nella.", "end", tag="leave")]),
    "main": N("Back again? The fire's going, at least.",
              variants=[dict(when={"time": {"from": 19, "to": 24}}, text="Evening. Mind Garrick, he's had one cider and thinks he's a philosopher."),
                        dict(when={"weather": "rain"}, text="Rain again. Half the village is in here and the other half's thinking about it.")],
              choices=[CH("What's the news around the village?", "news", tag="ask"),
                       CH("Could I use the hearth?", "hearth", tag="ask"),
                       CH("What do you sell?", "end", tag="trade", actions=[A("open_shop", shop="inn")]),
                       CH("How long have you kept the inn?", "history", tag="ask", once=True),
                       BYE]),
    "news": N("Bridge east is still out, so the carters are grumpy and the cider's selling. Sabine and Maren are at it over that empty stall. And Corin's been 'leaving tomorrow' for a week.",
              variants=[dict(when={"world_flag": "hollowatlas:repaired_pump"}, text="Tobin's pump runs again, so he's stopped sighing into his soup. The bridge east is still out. And Corin is still leaving tomorrow.")],
              choices=[CH("Anything else?", "main"), BYE]),
    "hearth": N("Help yourself, just don't burn my soup pot. You'll cook better with a recipe than with hope.", choices=[BYE]),
    "history": N("Eleven years. My mother before me. I know every traveler by their boots and their bad habits.",
                 [CH("And mine?", "history2"), BYE]),
    "history2": N("Wet boots. Asks a lot of questions. I'll reserve judgment on the bad habits.", [BYE]),
})

D["corin"] = dict(id="corin", npc="corin", entries=[
    dict(when={"quest_done": "m01", "quest_not_started": "m03"}, node="offer"),
    dict(when={"quest_active": "m03"}, node="waiting"),
    dict(when={"quest_active": "sq02"}, node="elsie"),
    dict(node="main"),
], nodes={
    "asleep": ASLEEP,
    "offer": N("You! You're the new one at the inn. Do you walk quickly? You look like you walk quickly.",
               [CH("Depends where to.", "offer2"), CH("Not today.", "end", tag="leave")]),
    "offer2": N("I've a parcel for a clinic up in the mountains, at Sableforge. Spare fittings, sealed and paid for. I'm meant to have it on the road already, but I've three other drops west and my strap broke, and, well. I'm late. I'm always late.",
                [CH("Happy to help. Where does it go first?", "accept", tag="quest", actions=[A("relationship", delta=5), A("choice_tmp", value="honest")] if False else [A("relationship", delta=5)]),
                 CH("Why not take it yourself?", "skeptic", tag="ask"),
                 CH("What does it pay?", "pay", tag="persuade")]),
    "skeptic": N("Because the clinic needs it sooner than I can get there, and because I trust you more than I trust my left boot. Which isn't saying much. Please?",
                 [CH("All right. I'll take it.", "accept", tag="quest"), CH("Let me think about it.", "end", tag="leave")]),
    "pay": N("Ten coins now, ten at the relay when it's logged. That's fair. That's more than fair, actually, don't tell anyone.",
             [CH("Make it fifteen up front.", "accept_pay", tag="persuade", actions=[A("coins", amount=15), A("relationship", delta=-2)]),
              CH("Ten's fine.", "accept", tag="quest", actions=[A("coins", amount=10)])]),
    "accept_pay": N("...Fifteen. Fine. You drive a hard bargain for someone who arrived wearing half the river.", nxt="accept"),
    "accept": N("Take it to the East Road Relay first. Jory logs everything that goes east, and he'll stamp it for the mountain route. Don't open it. Not that you would. It's only pump fittings, or so they tell me.",
                [CH("I'll see it there.", "end", tag="leave")],
                actions=[A("start_quest", quest="m03"), A("give_item", item="hollowatlas:sealed_parcel"), A("set_flag", flag="accepted_courier_work")]),
    "waiting": N("Still here? The relay's out past the watchtower. Long walk. Nice woods. Mind the woods.", [BYE]),
    "elsie": N("You're walking Elsie's old route? She made me walk it twice when I started. Said I needed to 'learn to be late properly'.", [BYE]),
    "main": N("Hello again!",
              variants=[dict(when={"quest_done": "m04"}, text="Bridge is still out, then. I suppose my parcel's having a nice long rest at the relay. Better than me.")],
              choices=[CH("Where are you headed?", "where", tag="ask"), BYE]),
    "where": N("East. Then west. Then probably east again. The roads don't care where you started.", [BYE]),
})

D["tobin"] = dict(id="tobin", npc="tobin", entries=[
    dict(when=stage("m02", "report"), node="done"),
    dict(when={"quest_active": "m02"}, node="progress"),
    dict(when={"flag": "met_nella", "quest_not_started": "m02"}, node="offer"),
    dict(node="main"),
], nodes={
    "asleep": ASLEEP,
    "offer": N("You're the one staying at Nella's. She said you'd be by, or she said I should be by, one of those. You any good with machines?",
               [CH("I can take a look.", "offer2", tag="quest"), CH("Not really.", "offer_no")]),
    "offer_no": N("Nor me. That's the problem. If you change your mind, the pump's in the orchard.", [CH("Tell me about the pump.", "offer2"), BYE]),
    "offer2": N("Orchard pump's dying. Coughs, spits, then nothing. I've hauled buckets from the brook for a week. Ordered a part from the smith weeks back and it never came. Have a look, would you? I keep a ledger at the house if you want the history.",
                [CH("I'll see what I can find.", "end", tag="quest", actions=[A("start_quest", quest="m02")])]),
    "progress": N("Any luck with the pump?",
                  [CH("The pin's sheared and the valve leather's gone.", "progress2", when={"evidence": ["pump_pin_sheared"]}),
                   CH("Still looking.", "end", tag="leave")]),
    "progress2": N("Leather I can understand. Pin's no surprise either. Garrick will make one if you ask him nicely, and if you don't, he'll make one anyway and complain about it.", [BYE]),
    "done": N("I heard it from the house. That sound. Water. You don't know how good that sounds.",
              [CH("It should hold for a while.", "done2", actions=[A("quest_event", event="talk", target="tobin_pump_done")])]),
    "done2": N("Here. It's not much, but it's honest, and the apples are this year's. Thank you.", [BYE]),
    "main": N("Afternoon.",
              variants=[dict(when={"world_flag": "hollowatlas:repaired_pump"}, text="Pump's still running. Every morning I check, like it's a sick calf.")],
              choices=[CH("How's the harvest looking?", "harvest", tag="ask"), BYE]),
    "harvest": N("Wheat's fair. Orchard's good if it gets water. Carrots are carrots.", [BYE]),
})

D["wenna"] = dict(id="wenna", npc="wenna", entries=[dict(node="main")], nodes={
    "asleep": ASLEEP,
    "main": N("Hello! You're not buying apples, are you? Nobody ever is when I'm talking.",
              [CH("What do you sell?", "end", tag="trade", actions=[A("open_shop", shop="ashby_produce")]),
               CH("How long have you kept the orchard?", "orchard", tag="ask"),
               CH("About the pump...", "pump", tag="ask", when={"quest_active": "m02"}),
               BYE]),
    "orchard": N("Twenty years with Tobin, and his father before that. The old trees were planted by someone who liked straight lines. I've been undoing it a branch at a time.", [BYE]),
    "pump": N("Tobin won't say it, but he's worried. If you can fix it, I'll owe you pie. Proper pie.", [BYE]),
})

D["garrick"] = dict(id="garrick", npc="garrick", entries=[
    dict(when={"quest_stage": {"quest": "m02", "stage": "parts"}, "not": {"has_item": {"item": "hollowatlas:pump_pin"}}}, node="pin"),
    dict(node="main"),
], nodes={
    "asleep": ASLEEP,
    "pin": N("Tobin's pump. Pin's sheared, is it. He ordered one from me. I sent the order east to the relay with the rest. Never came back.",
             [CH("Can you make one now?", "pin2", tag="quest")]),
    "pin2": N("Course I can. Give me a minute. ...There. Iron, not the soft brass he had before. That'll outlast the orchard.",
              [CH("Thank you.", "end", tag="leave")],
              actions=[A("give_item", item="hollowatlas:pump_pin"), A("relationship", delta=3)]),
    "main": N("Hm. Need something?",
              choices=[CH("Show me your work.", "end", tag="trade", actions=[A("open_shop", shop="smithy")]),
                       CH("What's the boy's name? Your apprentice.", "bram", tag="ask", once=True),
                       BYE]),
    "bram": N("Bram. Good hands, bad ears. He'll do.", [BYE]),
})

D["bram"] = dict(id="bram", npc="bram", entries=[
    dict(when=stage("sq01", "look"), node="ribbon"),
    dict(node="main"),
], nodes={
    "asleep": ASLEEP,
    "ribbon": N("The ribbon? Mum's blue one? ...I didn't take it. I saw a fox by our door the night before last, though. It had something in its mouth. I thought it was a rat. It was a very blue rat.",
                [CH("Which way did the fox go?", "ribbon2", tag="investigate")],
                actions=[A("evidence", id="bram_saw_fox"), A("quest_event", event="talk", target="ribbon_witness")]),
    "ribbon2": N("South-east, towards the woods. Foxes den out by the heron pool, Hester says. Don't tell Mum I was up that late.", [BYE]),
    "main": N("Hello! Do you want to see a nail I made? It's mostly straight.",
              [CH("Show me.", "nail"), BYE]),
    "nail": N("...See? Mostly. Master Holt says 'mostly' is the most dangerous word in smithing.", [BYE]),
})

D["ada"] = dict(id="ada", npc="ada", entries=[
    dict(when=stage("sq01", "stall"), node="stall"),
    dict(node="main"),
], nodes={
    "asleep": ASLEEP,
    "main": N("Welcome to Hobb and Daughters. The daughters are in Veylport, being expensive.",
              choices=[CH("Let's trade.", "end", tag="trade", actions=[A("open_shop", shop="general_store")]),
                       CH("I need leather for a pump valve.", "leather", tag="quest",
                          when={"quest_stage": {"quest": "m02", "stage": "parts"}, "not": {"has_item": {"item": "hollowatlas:valve_leather"}}}),
                       CH("Who decides who gets the empty stall?", "stall_q", tag="ask"),
                       BYE]),
    "leather": N("Valve leather? Tobin's pump, I'd wager. I've a cut of oiled hide that'll do. Six coins.",
                 [CH("Here's six.", "end", tag="trade", enabled_when={"coins_min": 6}, hint="You need 6 coins.",
                     actions=[A("coins", amount=-6), A("give_item", item="hollowatlas:valve_leather")]),
                  CH("Maybe later.", "end", tag="leave")]),
    "stall_q": N("I keep the market roll. The Hall says the stall goes to whoever the roll says. The roll says whatever I write. So, me.", [BYE]),
    "stall": N("So. You've been in the middle of the ribbon business. Who should have the empty stall, in your view? I'll listen. I may even agree.",
               [CH("Sabine. Her cloth deserves the front of the square.", "stall_s", tag="persuade"),
                CH("Maren. Bread feeds the whole village.", "stall_m", tag="persuade"),
                CH("Both. Split the day: bread in the morning, cloth after.", "stall_b", tag="persuade")]),
    "stall_s": N("Sabine, then. Maren will sulk for a week, but she'll live. I'll write it on the roll.", [BYE],
                 actions=[A("set_variant", npc="sabine", variant="stall_sabine"), A("world_flag", flag="hollowatlas:stall_b_sabine"),
                          A("world_flag", flag="hollowatlas:stall_b_resolved"), A("choice", quest="sq01", key="stall", value="sabine"),
                          A("relationship", npc="sabine", delta=8), A("relationship", npc="maren", delta=-4),
                          A("quest_event", event="talk", target="stall_decided")]),
    "stall_m": N("Maren. Sabine will call me a philistine, which is the nicest thing she's called me. I'll write it on the roll.", [BYE],
                 actions=[A("set_variant", npc="maren", variant="stall_maren"), A("world_flag", flag="hollowatlas:stall_b_maren"),
                          A("world_flag", flag="hollowatlas:stall_b_resolved"), A("choice", quest="sq01", key="stall", value="maren"),
                          A("relationship", npc="maren", delta=8), A("relationship", npc="sabine", delta=-4),
                          A("quest_event", event="talk", target="stall_decided")]),
    "stall_b": N("Share it? They'll hate that. ...Which probably means it's fair. Bread until one, cloth after. I'll write it on the roll.", [BYE],
                 actions=[A("set_variant", npc="maren", variant="shared_stall"), A("set_variant", npc="sabine", variant="shared_stall"),
                          A("world_flag", flag="hollowatlas:stall_b_shared"), A("world_flag", flag="hollowatlas:stall_b_resolved"),
                          A("choice", quest="sq01", key="stall", value="shared"),
                          A("relationship", npc="maren", delta=3), A("relationship", npc="sabine", delta=3),
                          A("quest_event", event="talk", target="stall_decided")]),
})

D["maren"] = dict(id="maren", npc="maren", entries=[
    dict(when=stage("sq04", "back"), node="buns_back"),
    dict(when={"quest_done": "m01", "quest_not_started": "sq04", "not": {"quest_active": "sq01"}}, node="buns_offer"),
    dict(when={"quest_active": "sq01", "hypothesis": {"case": "ribbon_case", "id": "maren_took_it"}}, node="accused"),
    dict(when=stage("sq01", "look"), node="ribbon"),
    dict(node="main"),
], nodes={
    "asleep": ASLEEP,
    "ribbon": N("Sabine's ribbon? She thinks I took it. As if I've time to steal ribbons between the second and third bake.",
                [CH("There's blue thread on your door post.", "thread", tag="evidence", when={"evidence": ["blue_thread"]}),
                 CH("Did you see anything?", "saw", tag="investigate"), BYE]),
    "thread": N("On my... let me see. Huh. Well, it wasn't me who put it there. Something brushed past in the night, the bins were knocked over. I blamed the cats.",
                [BYE], actions=[A("evidence", id="maren_bins")]),
    "saw": N("I saw flour. I always see flour. Ask the boy, he's up at all hours.", [BYE], actions=[A("quest_event", event="talk", target="ribbon_witness_m")]),
    "accused": N("I hear you think I took it. I hope you're enjoying your life in the village, because my bread won't be in it.",
                 [CH("I was wrong. I'm sorry.", "sorry", tag="persuade", actions=[A("relationship", delta=2)]), BYE],
                 actions=[A("relationship", delta=-3)]),
    "sorry": N("...Hmph. Apology noted. Not accepted. Noted.", [BYE]),
    "main": N("Bread, buns, and opinions. The first two cost money.",
              [CH("Let's see the bread.", "end", tag="trade", actions=[A("open_shop", shop="bakery")]), BYE]),
    "buns_offer": N("You've got legs that work. Mine stopped working at the third bake. Lom at the bridge gets a basket every week; he's alone out there and he eats like a heron. Would you?",
                    [CH("I'll take it to him.", "buns_go", tag="quest"),
                     CH("Let's see the bread first.", "end", tag="trade", actions=[A("open_shop", shop="bakery")]), BYE]),
    "buns_go": N("Don't eat any. I counted them. I didn't, but act as if I did.", [BYE],
                 actions=[A("start_quest", quest="sq04")]),
    "buns_back": N("Well? Did he eat them? Did he say anything about the honey?",
                   [CH("He said they were better than last week's.", "buns_done")]),
    "buns_done": N("Last week's were perfect. ...He's a liar and a good man. Here, for your trouble.", [BYE],
                   actions=[A("quest_event", event="talk", target="buns_reported")]),
})

D["sabine"] = dict(id="sabine", npc="sabine", entries=[
    dict(when={"quest_stage": {"quest": "sq01", "stage": "return"}, "has_item": {"item": "hollowatlas:blue_ribbon"}}, node="returned"),
    dict(when={"quest_active": "sq01"}, node="progress"),
    dict(when={"quest_not_started": "sq01"}, node="offer"),
    dict(node="main"),
], nodes={
    "asleep": ASLEEP,
    "offer": N("Have you seen a blue ribbon? Long, silk, embroidered at the ends? No, of course you haven't. Nobody has. It's simply gone.",
               [CH("When did it go missing?", "offer2", tag="ask"), BYE]),
    "offer2": N("Two nights ago. It hung on a hook by my door, where it's hung for six years. The festival's coming, and the empty stall goes to whoever the Hall favours. Without the ribbon I'm just another weaver. And Maren knows it.",
                [CH("I'll look into it.", "offer3", tag="quest"), CH("I'm sure it'll turn up.", "end", tag="leave")]),
    "offer3": N("Start at my door. And, frankly, at Maren's.", [BYE], actions=[A("start_quest", quest="sq01")]),
    "progress": N("Well? Have you found my ribbon or have you found an excuse?", [CH("Still looking.", "end", tag="leave")]),
    "returned": N("That's it! That's... chewed. Where on earth was it?",
                  [CH("In a fox den by the heron pool.", "returned2"),
                   CH("Does it matter? It's back.", "returned2")],
                  actions=[A("take_item", item="hollowatlas:blue_ribbon"), A("quest_event", event="talk", target="ribbon_returned")]),
    "returned2": N("A fox. Not Maren. ...I'll need to apologise to her, won't I. Ugh. Ada's deciding about the stall now; she wants to hear from you, since you've been in the middle of all this.", [BYE]),
    "main": N("Cloth, ribbon, thread. If you want something plain, go to Ada.", [BYE]),
})

D["elsie"] = dict(id="elsie", npc="elsie", entries=[
    dict(when={"quest_stage": {"quest": "sq03", "stage": "deliver"}, "has_item": {"item": "hollowatlas:herbal_remedy"}}, node="remedy"),
    dict(when=stage("sq02", "report"), node="route_done"),
    dict(when={"quest_active": "sq02"}, node="route_progress"),
    dict(when={"quest_not_started": "sq02", "quest_done": "m01"}, node="offer"),
    dict(node="main"),
], nodes={
    "asleep": ASLEEP,
    "offer": N("You there. You've got walking legs. Sit. No, don't sit, you'll get comfortable. Listen.",
               [CH("I'm listening.", "offer2")]),
    "offer2": N("Forty years I walked the Westreach for the post. The old route went the long way round: the west milestone, the lone oak, up Lark Hill, down through Mossgate on the cart track. Nobody uses it now. I want it walked once more and written down before my memory goes the way of my knees.",
                [CH("I'll walk it for you.", "offer3", tag="quest"), CH("Maybe another day.", "end", tag="leave")]),
    "offer3": N("Good. Take these notes. They're in my handwriting, so good luck. And mind the cart track. Folk used to say it was haunted. It wasn't. It was Tobin's father with a bottle.", [BYE],
                actions=[A("start_quest", quest="sq02"), A("give_item", item="hollowatlas:route_notes")]),
    "route_progress": N("Well? Did you find the milestone? They moved it once, you know. Fifty paces. Nobody asked me.", [CH("Still walking.", "end", tag="leave")]),
    "route_done": N("You walked it. All of it? Even the cart track? ...Who was camped there?",
                    [CH("Brigands. They won't be back soon.", "route_done2")]),
    "route_done2": N("Hm. In my day it was just foxes and Tobin's father. Here: this charm was mine for thirty years. Never once late while I wore it. Well. Rarely.", [BYE],
                     actions=[A("quest_event", event="talk", target="elsie_route_done")]),
    "remedy": N("What's that smell? Is that... Hester's brew? Give it here. If it's poison, at least it's local.",
                [CH("She says it's a good sign that it smells awful.", "remedy2")],
                actions=[A("take_item", item="hollowatlas:herbal_remedy"), A("quest_event", event="talk", target="remedy_given")]),
    "remedy2": N("...Oh. Oh, that's warm. All the way down. Tell Hester she's a menace and I love her.", [BYE]),
    "main": N("Still here? Good. The village needs people who notice things.",
              [CH("What did you notice, on the roads?", "roads", tag="ask", once=True), BYE]),
    "roads": N("That the post always arrives, and the reasons why it's late are always more interesting than the post.", [BYE]),
})

D["hester"] = dict(id="hester", npc="hester", entries=[
    dict(when={"quest_stage": {"quest": "sq03", "stage": "gather"}}, node="gathering"),
    dict(when={"quest_not_started": "sq03", "quest_done": "m01"}, node="offer"),
    dict(node="main"),
], nodes={
    "asleep": ASLEEP,
    "offer": N("Elsie Varrow's hip is aching again. She won't say so. She'll just walk slower and glare at the sky.",
               [CH("Is there anything that helps?", "offer2", tag="ask"), BYE]),
    "offer2": N("A warming remedy. Yarrow from the meadow south-west of here, feverfew from the riverbank. Three and two. I'd go myself, but my knees are worse than hers. Will you?",
                [CH("I'll gather them.", "taught", tag="quest", actions=[A("start_quest", quest="sq03")]), CH("Not now.", "end", tag="leave")]),
    "taught": N("Then listen: three yarrow, two feverfew, steeped slow at a good hearth. Never boil it. Elsie will pretend it doesn't help.", [BYE]),
    "gathering": N("Yarrow's the white clusters, feverfew's the little daisies. Pick, don't pull. Leave the roots and they'll come back. Then steep them slow at the inn hearth, never boil.",
                   [BYE]),
    "main": N("Feverfew's early this year. Everything's early this year.",
              [CH("Do you sell anything?", "end", tag="trade", actions=[A("open_shop", shop="herbalist")]), BYE]),
})

D["ilse"] = dict(id="ilse", npc="ilse", entries=[dict(node="main")], nodes={
    "asleep": ASLEEP,
    "main": N("Name and business.",
              variants=[dict(when={"ability": "windstep"}, text="Keep your feet light. You're getting better at it.")],
              choices=[CH("Just passing through.", "pass", tag="ask"),
                       CH("Could you teach me to fight better?", "train", tag="ask", when={"not": {"ability": "windstep"}}),
                       BYE]),
    "pass": N("Everyone's passing through until they're not. Keep to the roads after dark.", [BYE]),
    "train": N("I can teach you footwork. A quick step forward, out of trouble or into it. Ten coins for the drills. Free, if you've dealt with the lot camping on the old cart track.",
               [CH("Here's ten coins.", "learn", enabled_when={"coins_min": 10}, hint="You need 10 coins.", actions=[A("coins", amount=-10)]),
                CH("They're dealt with.", "learn", when={"world_flag": "hollowatlas:encounter_cleared:wagon_ambush"}),
                CH("Another time.", "end", tag="leave")]),
    "learn": N("Weight on the front foot. Push, don't jump. Again. Again. ...Good enough. Practise until it's boring.", [BYE],
               actions=[A("unlock_ability", ability="windstep")]),
})

D["oswin"] = dict(id="oswin", npc="oswin", entries=[dict(node="main")], nodes={
    "asleep": ASLEEP,
    "main": N("Mm. Hello.", [CH("How's the mill?", "mill", tag="ask"), BYE]),
    "mill": N("Turning. Water's lower than it should be this time of year. Wheel doesn't mind. I do.", [BYE]),
})

D["jory"] = dict(id="jory", npc="jory", entries=[
    dict(when=stage("m04", "decide"), node="decide"),
    dict(when={"quest_active": "m03"}, node="parcel"),
    dict(node="main"),
], nodes={
    "asleep": ASLEEP,
    "parcel": N("Parcel for the eastern route? Let me see. Sealed, paid, mountain clinic... yes. Logged. Stamped. It'll go east as soon as the bridge is up, along with everything else that's been waiting.",
                [CH("Everything else?", "parcel2", tag="ask")],
                actions=[A("quest_event", event="talk", target="jory_parcel"), A("take_item", item="hollowatlas:sealed_parcel")]),
    "parcel2": N("Six weeks of it. That crate there's been waiting longest. 'Orchard pump fittings', it says. Heavy for pump fittings. Not my business. Everything here is my business, but not that.", [BYE]),
    "decide": N("You've been looking at that crate. I saw you. Something on your mind?",
                [CH("The crate doesn't match its manifest, and the seal isn't yours.", "report", tag="evidence",
                    actions=[A("choice", quest="m04", key="report", value="reported"), A("set_flag", flag="report_choice_reported"),
                             A("quest_event", event="talk", target="seal_decided"), A("set_flag", flag="found_seal_mismatch")]),
                 CH("Nothing. Just curious.", "quiet", tag="ask",
                    actions=[A("choice", quest="m04", key="report", value="noted"), A("set_flag", flag="report_choice_noted"),
                             A("quest_event", event="talk", target="seal_decided"), A("set_flag", flag="found_seal_mismatch")])]),
    "report": N("Not my seal? Let me... no. No, that isn't mine. Close, but the notches are wrong. I log what I'm given. I'll write it down. I'll write it down twice.", [BYE]),
    "quiet": N("Curious is fine. Curious is how I ended up keeping a relay. Mind the crates.", [BYE]),
    "main": N("Relay's open. Nothing's moving east, mind.", [CH("Why not?", "why", tag="ask"), BYE]),
    "why": N("Eastfall bridge washed out in the spring melt. Lom down at the end of the road will tell you all about it. At length.", [BYE]),
})

D["lom"] = dict(id="lom", npc="lom", entries=[
    dict(when=stage("m04", "bridge"), node="bridge"),
    dict(when=stage("sq04", "deliver"), node="buns"),
    dict(node="main"),
], nodes={
    "asleep": ASLEEP,
    "bridge": N("When will it reopen? Ha. When the masons come, and the masons come when the money comes, and the money comes from somewhere east of a bridge that isn't there. You see the difficulty.",
                [CH("So the road east is closed.", "bridge2")]),
    "bridge2": N("For now. They'll fix it. Someone always does, in the end. Until then, Brindlecross is as far east as anyone goes.", [BYE],
                 actions=[A("quest_event", event="talk", target="lom_bridge")]),
    "main": N("Bridge is out. Has been since the melt.", [CH("Can I cross anyway?", "cross"), BYE]),
    "cross": N("Not unless you've wings or a death wish. There's a ladder down by the old stub, if you fall in. People fall in.", [BYE]),
    "buns": N("Is that Maren's basket? It is. It is! Hand it over before the river smells it.",
              [CH("She says don't eat them all at once.", "buns2")],
              actions=[A("quest_event", event="talk", target="buns_delivered")]),
    "buns2": N("Too late. Tell her they're better than last week's. Don't tell her I said that every week.", [BYE]),
})

for tree in D.values():
    write("dialogue", tree)

# notice board as a narrator tree
write("dialogue", dict(id="notice_board", npc=None, entries=[dict(node="board")], nodes={
    "board": N("Pinned notes, rain-curled:\n- 'LOST: blue silk ribbon, embroidered. Reward. - S. Reed'\n- 'Pump parts still awaited. - T. Ashby'\n- 'Eastfall crossing CLOSED until repairs. - Bridgewarden'\n- 'Herb gatherers wanted, see Hester.'",
               [CH("(Done reading.)", "end", tag="leave")], speaker="narrator")}))

# =====================================================================================  INTERACTIONS

INTERACTIONS = dict(id="brindlecross", interactions=[
    dict(id="notice_board", anchor="notice_board.sign", type="read", text="",
         actions=[A("open_dialogue", tree="notice_board", node="board")]),
    dict(id="inn_rest", anchor="inn.bed_room_guest", type="rest", when={"has_item": {"item": "hollowatlas:room_key"}},
         locked_text="This room is let to someone. Ask Nella about a room.",
         text="You sleep through the night to the sound of rain on the roof.",
         actions=[A("checkpoint", anchor="inn.room_guest")]),
    dict(id="hearth", anchor="inn.hearth_block", type="station", station="hearth", text="The hearth is warm and the pot hook is free."),
    dict(id="overlook_sign", anchor="overlook_cairn.sign", type="read",
         text="Someone has carved a little compass rose into the cairn. Brindlecross smokes quietly to the south-west."),
    dict(id="milestone", anchor="milestone_west.sign", type="read", text="An old milestone, freshly painted. The numbers have been corrected at least twice."),
    dict(id="bridge_sign", anchor="bridge_out.sign", type="read", text="'BRIDGE OUT'. Beyond the barrier, the gorge drops away to white water."),
    # --- M02 pump
    dict(id="pump", anchor="orchard_pump.body", type="inspect", once=True,
         text="An old hand pump. The handle moves freely, far too freely.",
         variants=[
             dict(id="pump_fix", when={"quest_stage": {"quest": "m02", "stage": "repair"}, "has_item": {"item": "hollowatlas:pump_pin"}},
                  text="You seat the new valve leather, drive the iron pin home and work the handle. A cough, a gurgle, and then water.",
                  actions=[A("take_item", item="hollowatlas:pump_pin"), A("take_item", item="hollowatlas:valve_leather"),
                           A("world_flag", flag="hollowatlas:repaired_pump"), A("sound", sound="minecraft:item.bucket.empty")]),
             dict(id="pump_look", when={"quest_active": "m02"},
                  text="The pin that joins handle to plunger has sheared clean through, and the leather valve inside has dried and cracked like old bark. Simple, honest wear.",
                  evidence="pump_pin_sheared"),
             dict(id="pump_running", when={"world_flag": "hollowatlas:repaired_pump"}, once=False,
                  text="The pump works smoothly now. Tobin has polished the handle."),
         ]),
    dict(id="pump_trough", anchor="orchard_pump.trough", type="inspect", once=True, when={"quest_active": "m02"},
         locked_text="A stone trough beside the pump.",
         text="Bone dry, with a rim of green where the water used to sit. The orchard's been thirsty for a while.", evidence="dry_trough"),
    dict(id="orchard_ledger", anchor="farmhouse.table_block", type="inspect", once=True, when={"quest_active": "m02"},
         locked_text="A table with a ledger on it. Not yours to read.",
         text="Tobin's orchard ledger, in a careful hand. 'Pin and valve leather ordered from G. Holt, sent east with the relay carter for brass fittings.' A later line: 'Still waiting.' Then: 'Still waiting.' Then nothing.",
         evidence="ledger_order"),
    # --- SQ01 ribbon
    dict(id="ribbon_hook", anchor="weaver_house.post", type="inspect", once=True, when={"quest_active": "sq01"},
         locked_text="A lantern post by the weaver's door.",
         text="An empty brass hook by the door, polished from years of use. In the mud below: small, four-toed prints, too neat for a dog.",
         evidence="fox_tracks"),
    dict(id="ribbon_thread", anchor="bakery.post", type="inspect", once=True, when={"quest_active": "sq01"},
         locked_text="A lantern post by the bakery door.",
         text="A few strands of bright blue silk caught on the splintered post, at about knee height.", evidence="blue_thread"),
    dict(id="fox_den", anchor="fox_den", dy=-1, type="inspect", once=True,
         text="A fox den dug under the roots. It smells exactly like a fox den.",
         variants=[dict(id="ribbon_found", when={"quest_stage": {"quest": "sq01", "stage": "find"}},
                        text="Tucked into the mouth of the den, among feathers and a stolen glove: a long blue silk ribbon, embroidered at the ends.",
                        actions=[A("give_item", item="hollowatlas:blue_ribbon")], evidence="ribbon_in_den")]),
    # --- M04 relay
    dict(id="relay_manifest", anchor="relay.lectern_block", type="inspect", once=True, when={"quest_active": "m04"},
         locked_text="The relay ledger. Jory guards it like a hen.",
         text="The eastern manifest, in Jory's tidy hand: 'Six crates. Orchard pump fittings, for points east. Relay seal applied.'",
         evidence="manifest_pump"),
    dict(id="relay_crate", anchor="relay.crate_block", type="inspect", once=True, when={"quest_active": "m04"},
         locked_text="Barrels and crates waiting for the bridge.",
         text="The crate barely shifts when you lean on it. Through a split slat: polished brass, finely threaded, packed tight in straw. Nothing like the plain iron and leather of Tobin's pump.",
         evidence="crate_brass"),
    dict(id="relay_seal", anchor="relay.strongbox", type="inspect", once=True, when={"quest_active": "m04"},
         locked_text="A locked strongbox.",
         text="The strongbox holds spare seals and the crate's paperwork. The crate's wax seal is pressed with a ring of small notches. Jory's relay stamp, on the paper beside it, is a plain wheel.",
         evidence="seal_notches"),
    # --- discoverables
    dict(id="lone_oak_cache", anchor="lone_oak.loose_earth", type="cache",
         text="The marker stones point here. The earth is loose. A shallow barrel lid, and inside: a little purse and a locket wrapped in oilcloth.",
         repeat_text="Just loose earth now.",
         loot=[dict(item="hollowatlas:old_coin", min=3, max=5), dict(item="hollowatlas:silver_locket")]),
    dict(id="wagon_clue", anchor="abandoned_wagon.clue", type="inspect", once=True,
         text="An overturned barrel from a broken cart. Burnt into the lid: an old courier mark, and a date nearly forty years gone. Someone kept it.",
         actions=[A("give_item", item="minecraft:sweet_berries", count=3)]),
    dict(id="cave_cache", anchor="cave_cache.box", type="cache", when={"world_flag": "hollowatlas:encounter_cleared:cave_camp"},
         locked_text="Something is stored here, but the camp's owners are close by.",
         text="The brigands' stash: some coin, a good hatchet and a few trinkets that were surely someone else's.",
         loot=[dict(item="hollowatlas:old_coin", min=4, max=7), dict(item="hollowatlas:holt_hatchet"), dict(item="minecraft:leather_chestplate")]),
    dict(id="camp_chest", anchor="cave_camp.chest", type="inspect", once=True,
         text="Supply lists, a bad map of the east road, and a lot of complaints about the bridge."),
    # --- shrine puzzle
    dict(id="lever_sun", anchor="old_shrine.lever_sun", type="puzzle", puzzle="old_shrine", symbol="sun", text="The lever under the bright stone clicks down."),
    dict(id="lever_river", anchor="old_shrine.lever_river", type="puzzle", puzzle="old_shrine", symbol="river", text="The lever under the blue tile clicks down."),
    dict(id="lever_stone", anchor="old_shrine.lever_stone", type="puzzle", puzzle="old_shrine", symbol="stone", text="The lever under the carved stone clicks down."),
    dict(id="shrine_cache", anchor="old_shrine.cache", type="cache", when={"world_flag": "hollowatlas:shrine_solved"},
         locked_text="Stone.",
         text="Under the hatch, wrapped in waxed cloth: a brass-rimmed lens on a cord. When you look through it, the world seems to hold its breath.",
         actions=[A("unlock_ability", ability="echo_sight")]),
    # --- gathering nodes
] + [dict(id=f"yarrow_{i}", anchor=f"yarrow_{i}", type="gather", text="You cut a few sprigs of yarrow.",
          loot=[dict(item="hollowatlas:yarrow", min=1, max=2)], regrow_minutes=8, depleted_state="minecraft:air",
          ripe_state="minecraft:azure_bluet") for i in range(1, 5)]
  + [dict(id=f"feverfew_{i}", anchor=f"feverfew_{i}", type="gather", text="You pick a few heads of feverfew.",
          loot=[dict(item="hollowatlas:feverfew", min=1, max=2)], regrow_minutes=8, depleted_state="minecraft:air",
          ripe_state="minecraft:oxeye_daisy") for i in range(1, 4)]
  + [dict(id=f"berries_{i}", anchor=f"berries_{i}", type="gather", text="You fill a palm with berries.",
          loot=[dict(item="minecraft:sweet_berries", min=2, max=4)], regrow_minutes=10,
          depleted_state="minecraft:sweet_berry_bush[age=1]", ripe_state="minecraft:sweet_berry_bush[age=3]") for i in range(1, 4)]
  + [dict(id=f"apple_tree_{i}", anchor=f"apple_tree_{i}", type="gather", text="You shake a branch and catch what falls.",
          loot=[dict(item="minecraft:apple", min=1, max=3)], regrow_minutes=12) for i in range(1, 4)]
  + [dict(id="carrots_1", anchor="carrots_1", type="gather", text="You pull a few carrots from the end of the row. Wenna said you could.",
          loot=[dict(item="minecraft:carrot", min=2, max=3)], regrow_minutes=15,
          depleted_state="minecraft:carrots[age=0]", ripe_state="minecraft:carrots[age=7]"),
     dict(id="carrots_2", anchor="carrots_2", type="gather", text="A few more carrots.",
          loot=[dict(item="minecraft:carrot", min=1, max=3)], regrow_minutes=15,
          depleted_state="minecraft:carrots[age=0]", ripe_state="minecraft:carrots[age=7]"),
     dict(id="potatoes_1", anchor="potatoes_1", type="gather", text="You dig up a couple of potatoes.",
          loot=[dict(item="minecraft:potato", min=2, max=3)], regrow_minutes=15,
          depleted_state="minecraft:potatoes[age=0]", ripe_state="minecraft:potatoes[age=7]")],
    puzzles=[dict(id="old_shrine", sequence=["sun", "river", "stone"], solved_flag="hollowatlas:shrine_solved",
                  reset_text="The levers spring back up with a grinding clunk. Wrong order.",
                  solved_text="Deep in the plinth something unlatches. The iron hatch in the floor stands open.",
                  on_solve=[A("discover", site="old_shrine")])])
write("interactions", INTERACTIONS)

# =====================================================================================  CASES

write("cases", dict(id="ribbon_case", title="The Missing Ribbon", summary="Sabine's blue festival ribbon vanished from its hook two nights ago.",
                    evidence=[
                        dict(id="fox_tracks", title="Tracks by the hook", text="Small four-toed prints in the mud under the empty hook.", kind="fact", source="Weaver's door"),
                        dict(id="blue_thread", title="Blue thread", text="Strands of blue silk snagged at knee height on the bakery post.", kind="fact", source="Bakery"),
                        dict(id="maren_bins", title="Knocked-over bins", text="Maren says something knocked her bins over in the night; she blamed the cats.", kind="testimony", source="Maren"),
                        dict(id="bram_saw_fox", title="A very blue rat", text="Bram saw a fox by Reed House carrying something, heading south-east.", kind="testimony", source="Bram"),
                        dict(id="ribbon_in_den", title="The ribbon", text="Found in a fox den near the heron pool.", kind="fact", source="Mossgate Wood"),
                    ],
                    hypotheses=[
                        dict(id="maren_took_it", text="Maren took it to win the stall", needs=["blue_thread"],
                             reaction="It's a theory. The thread was at knee height, though."),
                        dict(id="fox_took_it", text="An animal carried it off", needs=["fox_tracks", "bram_saw_fox"],
                             reaction="Tracks, a witness, and a trail heading south-east."),
                        dict(id="sabine_lost_it", text="Sabine simply misplaced it", needs=["fox_tracks"],
                             reaction="Possible. Sabine won't like hearing it."),
                    ]))
write("cases", dict(id="relay_case", title="The Crate at the Relay", summary="A crate bound east has waited at the relay since the bridge went out.",
                    visible_when={"any": [{"quest_active": "m04"}, {"quest_done": "m04"}]},
                    evidence=[
                        dict(id="manifest_pump", title="The manifest", text="Lists the crate as orchard pump fittings, relay seal applied.", kind="fact", source="Relay ledger"),
                        dict(id="crate_brass", title="What's inside", text="Heavy, finely threaded brass fittings packed in straw. Not pump parts like Tobin's.", kind="fact", source="Relay crate"),
                        dict(id="seal_notches", title="The seal", text="The crate's wax seal has a ring of notches; the relay's own stamp is a plain wheel.", kind="fact", source="Relay strongbox"),
                        dict(id="ledger_order", title="Tobin's order", text="Tobin's pin and valve leather were sent east with the relay carter and never came back.", kind="fact", source="Orchard ledger"),
                        dict(id="pump_pin_sheared", title="The pump", text="Plain iron pin, leather valve. Ordinary wear.", kind="fact", source="Orchard pump"),
                    ],
                    hypotheses=[
                        dict(id="clerical", text="A clerical mistake: wrong label, wrong seal", needs=["manifest_pump", "crate_brass"],
                             reaction="Mistakes happen. Two at once is less common."),
                        dict(id="false_manifest", text="Something is being shipped under a false label", needs=["manifest_pump", "crate_brass", "seal_notches"],
                             reaction="A guess. But the label, the contents and the seal all disagree."),
                        dict(id="jory_careless", text="Jory is careless with his paperwork", needs=["manifest_pump"],
                             reaction="Jory would be mortified to hear it."),
                    ]))

# =====================================================================================  SHOPS / RECIPES

write("shops", dict(id="general_store", title="Hobb & Daughters General Goods", npc="ada", anchor="store.counter_front", restock_days=1,
                    sells=[dict(item="minecraft:bread", price=2, stock=8), dict(item="minecraft:apple", price=1, stock=10),
                           dict(item="minecraft:cooked_mutton", price=4, stock=5), dict(item="minecraft:mutton", price=2, stock=6),
                           dict(item="minecraft:leather_boots", price=8, stock=2), dict(item="minecraft:leather_helmet", price=10, stock=2),
                           dict(item="minecraft:torch", price=1, stock=12, count=2)],
                    buys={"hollowatlas:yarrow": 2, "hollowatlas:feverfew": 2, "minecraft:sweet_berries": 1, "minecraft:apple": 1,
                          "hollowatlas:old_coin": 6, "hollowatlas:silver_locket": 30, "minecraft:carrot": 1, "minecraft:potato": 1,
                          "minecraft:leather": 3, "minecraft:rotten_flesh": 0},
                    buy_back_rate=0.4))
write("shops", dict(id="smithy", title="Brindlecross Smithy", npc="garrick", anchor="smithy.counter_front", restock_days=3,
                    sells=[dict(item="hollowatlas:ash_quarterstaff", price=30, stock=1), dict(item="hollowatlas:holt_hatchet", price=55, stock=1),
                           dict(item="minecraft:leather_chestplate", price=18, stock=1), dict(item="minecraft:leather_leggings", price=14, stock=1),
                           dict(item="minecraft:shield", price=20, stock=1)],
                    buys={"hollowatlas:holt_hatchet": 25, "hollowatlas:ash_quarterstaff": 12, "minecraft:iron_nugget": 1}))
write("shops", dict(id="bakery", title="The Bakehouse", npc="maren", anchor="bakery.inside", restock_days=1,
                    sells=[dict(item="hollowatlas:honey_bun", price=3, stock=6), dict(item="minecraft:bread", price=2, stock=10),
                           dict(item="minecraft:cookie", price=1, stock=8, count=2), dict(item="minecraft:pumpkin_pie", price=5, stock=2)],
                    buys={"minecraft:wheat": 1, "minecraft:sweet_berries": 1}))
write("shops", dict(id="inn", title="The Rain & Lantern", npc="nella", anchor="inn.counter_front", restock_days=1,
                    sells=[dict(item="hollowatlas:hearth_stew", price=5, stock=4), dict(item="minecraft:bread", price=2, stock=6),
                           dict(item="minecraft:baked_potato", price=2, stock=6)],
                    buys={"minecraft:carrot": 1, "minecraft:potato": 1, "minecraft:mutton": 2}))
write("shops", dict(id="ashby_produce", title="Ashby Produce", npc="wenna", anchor="stall_a.counter_front", restock_days=1,
                    sells=[dict(item="minecraft:apple", price=1, stock=12), dict(item="minecraft:carrot", price=1, stock=10),
                           dict(item="minecraft:potato", price=1, stock=10), dict(item="minecraft:pumpkin_pie", price=4, stock=1)],
                    buys={"minecraft:sweet_berries": 1}))
write("shops", dict(id="herbalist", title="Hester's Remedies", npc="hester", anchor="herb_house.inside", restock_days=2,
                    sells=[dict(item="hollowatlas:trail_tonic", price=8, stock=2)],
                    buys={"hollowatlas:yarrow": 3, "hollowatlas:feverfew": 3, "minecraft:sweet_berries": 1}))

write("recipes", dict(id="hearth_stew", station="hearth", name="Hearth Stew",
                      inputs=[dict(item="minecraft:mutton", count=1), dict(item="minecraft:carrot", count=1), dict(item="minecraft:potato", count=1)],
                      output=dict(item="hollowatlas:hearth_stew", count=1)))
write("recipes", dict(id="baked_apple_bun", station="hearth", name="Honey Bun (berry glaze)",
                      inputs=[dict(item="minecraft:bread", count=1), dict(item="minecraft:sweet_berries", count=2)],
                      output=dict(item="hollowatlas:honey_bun", count=1)))
write("recipes", dict(id="herbal_remedy", station="hearth", name="Hester's Warming Remedy", requires_flag="recipe_remedy",
                      inputs=[dict(item="hollowatlas:yarrow", count=3), dict(item="hollowatlas:feverfew", count=2)],
                      output=dict(item="hollowatlas:herbal_remedy", count=1)))
write("recipes", dict(id="trail_tonic", station="hearth", name="Trail Tonic", requires_flag="recipe_tonic",
                      inputs=[dict(item="hollowatlas:yarrow", count=1), dict(item="hollowatlas:feverfew", count=1), dict(item="minecraft:sweet_berries", count=1)],
                      output=dict(item="hollowatlas:trail_tonic", count=1)))

# =====================================================================================  ENCOUNTERS

write("encounters", dict(id="wagon_ambush", anchor="abandoned_wagon.ambush", trigger_radius=20, daily=True,
                         spawns=[dict(kind="brigand", dx=0, dz=0), dict(kind="slinger", dx=6, dz=-4)],
                         start_text="Two figures step out from behind the broken cart.",
                         on_clear=[A("coins", amount=6)]))
write("encounters", dict(id="cave_camp", anchor="cave_camp", trigger_radius=14, daily=False,
                         spawns=[dict(kind="brigand", dx=2, dz=-2), dict(kind="brigand", dx=-3, dz=1), dict(kind="slinger", dx=0, dz=-5)],
                         start_text="Firelight, voices, and then silence. They've seen you.",
                         on_clear=[A("message", text="The camp is quiet. Whatever they kept here is yours to find.")]))

# =====================================================================================  WORLD CHANGES

write("world_changes", dict(id="brindlecross", changes=[
    dict(id="pump_water", flag="hollowatlas:repaired_pump",
         blocks=[dict(anchor="orchard_pump.trough", state="minecraft:water_cauldron[level=3]")],
         otherwise=[dict(anchor="orchard_pump.trough", state="minecraft:cauldron")]),
    dict(id="shrine_hatch", flag="hollowatlas:shrine_solved",
         blocks=[dict(anchor="old_shrine.hatch", state="minecraft:iron_trapdoor[facing=north,half=top,open=true,powered=false,waterlogged=false]")],
         otherwise=[dict(anchor="old_shrine.hatch", state="minecraft:iron_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]")]),
] + [dict(id=f"stall_b_{who}", flag=f"hollowatlas:stall_b_{who}",
          blocks=[dict(anchor="stall_b.work", dx=dx, dy=3, dz=dz, state=wool)
                  for dx in range(-2, 3) for dz in range(-1, 2)
                  for wool in [("minecraft:light_blue_wool" if who == "sabine" else "minecraft:yellow_wool" if who == "maren"
                               else ("minecraft:light_blue_wool" if (dx + 2) % 2 == 0 else "minecraft:yellow_wool"))]])
     for who in ["sabine", "maren", "shared"]]))

# =====================================================================================  SITES

write("sites", dict(id="westreach", sites=[
    dict(id="brindlecross", name="Brindlecross", anchor="plaza", kind="village", radius=30),
    dict(id="rain_lantern", name="The Rain & Lantern", anchor="inn.door_out", kind="inn", radius=8),
    dict(id="west_milestone", name="West Milestone", anchor="milestone_west.sign", kind="landmark", radius=10),
    dict(id="brindle_mill", name="Brindle Mill", anchor="mill.door_out", kind="landmark", radius=12),
    dict(id="ashby_farm", name="Ashby Farm & Orchard", anchor="farmhouse.door_out", kind="farm", radius=20),
    dict(id="east_watch", name="East Watchtower", anchor="watchtower.door_out", kind="landmark", radius=10),
    dict(id="east_relay", name="East Road Relay", anchor="relay.door_out", kind="landmark", radius=14),
    dict(id="eastfall", name="Eastfall Crossing (closed)", anchor="east_gate", kind="landmark", radius=14),
    dict(id="lark_hill", name="Lark Hill Overlook", anchor="overlook_cairn.view", kind="discovery", radius=8),
    dict(id="lone_oak", name="The Lone Oak", anchor="lone_oak.stand", kind="discovery", radius=10),
    dict(id="heron_pool", name="Heron Pool", anchor="heron_pool", kind="discovery", radius=12),
    dict(id="cart_track", name="Old Cart Track", anchor="wagon", kind="discovery", radius=12),
    dict(id="hobs_hollow", name="Hob's Hollow", anchor="cave_mouth", kind="cave", radius=10),
    dict(id="old_shrine", name="The Old Shrine", anchor="shrine", kind="discovery", radius=8),
]))

# =====================================================================================  ABILITIES

write("abilities", dict(id="windstep", name="Windstep", category="movement", pool="stamina", cost=30, cooldown=60,
                        token_item="hollowatlas:technique_windstep",
                        description="A trained burst of footwork: a fast step forward, out of trouble or into it.",
                        unlock={"comment": "Taught by Ilse Brand at the watch."},
                        branches=[dict(id="long_stride", name="Long Stride", description="(Future) Further, at greater cost.", cost=1),
                                  dict(id="side_step", name="Side Step", description="(Future) Step in any direction.", cost=1)]))
write("abilities", dict(id="echo_sight", name="Echo Sight", category="investigation", pool="focus", cost=40, cooldown=240,
                        token_item="hollowatlas:tracer_lens",
                        description="Look through the old lens: recent traces and hidden things nearby shimmer for a few moments.",
                        unlock={"comment": "Found in the Old Shrine."},
                        branches=[dict(id="long_echo", name="Lingering Echo", description="(Future) Traces last longer.", cost=1)]))

for q in QUESTS:
    write("quests", q)
print("content written:", len(QUESTS), "quests,", len(D) + 1, "dialogue trees")

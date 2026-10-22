"""Writes the Post Rider advancements (data/postrider/advancement/*.json) and their names in en_us.json.

Usage: gen_advancements.py   (run from the repository root)
Every post-specific criterion uses the postrider:post_event trigger (see PostEventTrigger and PostEvents).
"""
import json
import os

ADV_DIR = "src/main/resources/data/postrider/advancement"
LANG = "src/main/resources/assets/postrider/lang/en_us.json"
COLOURS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
           "blue", "brown", "green", "red", "black"]
CREW = ["chris", "mattie", "eli", "rosa", "jeb", "tom", "clara", "silas", "hattie", "wyatt", "ike", "lottie", "gus", "nell",
        "abe", "billy", "ada", "hank", "sadie", "juan"]


def post_event(event, rider=None, at_least=None):
    conditions = {"event": event}
    if rider:
        conditions["rider"] = rider
    if at_least:
        conditions["at_least"] = at_least
    return {"trigger": "postrider:post_event", "conditions": conditions}


def has_item(item):
    return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item}]}}


# id: (parent, icon, title, description, frame, criteria, requirements or None for all, extra display, rewards)
ADVANCEMENTS = [
    ("open_for_business", None, "postrider:red_mailbox", "Open for Business", "Get hold of a mailbox", "task",
     {"mailbox": has_item("#postrider:mailboxes")}, None, {"background": "minecraft:textures/block/spruce_planks.png"}, None),
    ("hang_out_your_shingle", "open_for_business", "minecraft:oak_sign", "Hang Out Your Shingle", "Give a mailbox an address", "task",
     {"named": post_event("named_mailbox")}, None, {}, None),
    ("paint_the_town", "open_for_business", "minecraft:red_dye", "Paint the Town", "Dye a mailbox", "task",
     {"dyed": post_event("dyed_mailbox"),
      "crafted": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": ["postrider:%s_mailbox" % c for c in COLOURS if c != "white"]}]}}},
     [["dyed", "crafted"]], {}, None),
    ("rainbow_route", "paint_the_town", "postrider:lime_mailbox", "Rainbow Route", "Collect a mailbox in all 16 colours", "challenge",
     {c: has_item("postrider:%s_mailbox" % c) for c in COLOURS}, None, {}, {"experience": 100}),
    ("first_class", "hang_out_your_shingle", "postrider:parcel", "First Class", "Post something to another mailbox", "task",
     {"sent": post_event("sent")}, None, {}, None),
    ("signed_sealed_delivered", "first_class", "postrider:written_letter", "Signed, Sealed, Delivered", "Send a letter on its own", "task",
     {"letter": post_event("sent_letter")}, None, {}, None),
    ("pen_pal", "signed_sealed_delivered", "postrider:letter", "Pen Pal", "Send 10 letters", "goal",
     {"letters": post_event("sent_letter", at_least=10)}, None, {}, None),
    ("you_ve_got_mail", "hang_out_your_shingle", "postrider:blue_mailbox", "You've Got Mail", "Find a delivery waiting in your mailbox", "task",
     {"received": post_event("received")}, None, {}, None),
    ("neither_snow_nor_rain", "you_ve_got_mail", "minecraft:lantern", "Neither Snow nor Rain",
     "Receive mail that crossed water by barge or spent a night on the trail", "goal",
     {"weathered": post_event("received_weathered")}, None, {}, None),
    ("chris_is_here", "open_for_business", "minecraft:saddle", "Chris Is Here!", "Be greeted by Chris on the road", "task",
     {"chris": post_event("greeted", rider="chris")}, None, {}, None),
    ("meet_the_crew", "chris_is_here", "minecraft:name_tag", "Meet the Crew", "Be greeted by all 20 riders", "challenge",
     {r: post_event("greeted", rider=r) for r in CREW}, None, {}, {"experience": 100}),
    ("home_on_the_range", "open_for_business", "minecraft:campfire", "Home on the Range", "Come across a rider's camp", "task",
     {"camp": post_event("found_camp")}, None, {}, None),
    ("lost_and_found", "first_class", "minecraft:compass", "Lost and Found", "Recover a Lost Parcel", "task",
     {"recovered": post_event("recovered")}, None, {}, None),
    ("davy_jones_locker", "lost_and_found", "minecraft:heart_of_the_sea", "Davy Jones' Locker",
     "Recover a Lost Parcel from a wreck on the water", "goal",
     {"wreck": post_event("recovered_wreck")}, None, {}, None),
    ("highway_robbery", "lost_and_found", "minecraft:skeleton_skull", "Highway Robbery",
     "Open the Lost Parcel of a rider you brought down", "goal",
     {"robbed": post_event("recovered_own_kill")}, None, {"hidden": True}, None),
]


def main():
    os.makedirs(ADV_DIR, exist_ok=True)
    with open(LANG, encoding="utf-8") as f:
        lang = json.load(f)
    for adv_id, parent, icon, title, description, frame, criteria, requirements, extra, rewards in ADVANCEMENTS:
        key = "advancements.postrider." + adv_id
        display = {
            "icon": {"id": icon},
            "title": {"translate": key + ".title"},
            "description": {"translate": key + ".description"},
            "frame": frame,
            "show_toast": True,
            "announce_to_chat": True,
            "hidden": extra.get("hidden", False),
        }
        if "background" in extra:
            display["background"] = extra["background"]
        advancement = {}
        if parent:
            advancement["parent"] = "postrider:" + parent
        advancement["display"] = display
        advancement["criteria"] = criteria
        advancement["requirements"] = requirements or [[name] for name in criteria]
        if rewards:
            advancement["rewards"] = rewards
        with open(os.path.join(ADV_DIR, adv_id + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(advancement, f, indent=2)
            f.write("\n")
        lang[key + ".title"] = title
        lang[key + ".description"] = description
    lang["stat.postrider.letters_sent"] = "Letters Sent"
    lang["item.postrider.journey.barge"] = "Crossed water by barge"
    lang["item.postrider.journey.night"] = "Spent a night on the trail"
    lang["item.postrider.journey.nights"] = "Spent %s nights on the trail"
    with open(LANG, "w", encoding="utf-8", newline="\n") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(len(ADVANCEMENTS), "advancements")


main()

# Post Rider

Mounted mail for Minecraft. Put up a mailbox, give it an address, and post a parcel or a letter to any other
mailbox in the world. One of twenty riders appears in a puff of smoke, takes the mail, whistles up a horse and
rides it there, across country, around ravines, over lakes by ferry barge, camping out under canvas at night.
Usually it arrives. Sometimes the riders don't make it, and you'll have to go and find what's left.

For Minecraft 1.21.1 with NeoForge 21.1 (and for Minecraft 26.3, a separate download from the `main` branch: the jar's
name says which). Needed on both the server and every client; it doesn't need any other mods.

## Mailboxes

- **Craft one:** three iron ingots across the top, iron, a wooden chest and iron in the middle, and a wooden fence
  post underneath. Dye it any of the 16 colours by crafting it with a dye, any time.
- **Give it an address** on the Settings tab: 3 to 24 letters, numbers or simple punctuation. Every address must be
  unique, and the screen tells you as you type if there's a problem.
- **Inbox:** holds 27 deliveries. The flag goes up when there's mail.
- **Send tab:** put up to 9 stacks in the parcel slots, pick an address from the ledger, and press Send. A letter on
  its own goes as a letter; anything else is wrapped in a parcel.
- **Your box only:** only the owner can open or break it. Explosions can't destroy it.
- **Sky view:** put it somewhere with a view of the sky, so riders can reach it. You get a warning if it hasn't got one.

## Letters

Craft a letter from paper, a feather and an ink sac, then use it to write. Signed letters can be read by anyone who
holds them, and every delivery is postmarked with the day it was posted and how long it took.

## The riders

Twenty riders, each always the same person with their own face, outfit and horse. That's Chris on Lightning (the
fastest horse on the road), Martha "Mattie" Cole, "One-Eyed" Nell Harper, old Gus Tanner on Molasses (the
slowest), and sixteen more. Only one of each is out at a time.

On the road a rider:

- **Collects** between 6am and dusk. Mail posted later waits for the next collection.
- **Plans his route** with his own map: after the pickup he steps away from the mailbox, opens a big paper map and
  works out the whole trip, then plans the blocks ahead as he rides. He goes round ravines, cliffs and bays, uses
  caves and tunnels when they're shorter, leaps small gaps, and gets down and climbs ladders on foot where his horse
  can't go. He never wades or swims, and never digs or opens doors.
- **Avoids hazards:** he keeps off lava, fire, cactus and powder snow.
- **Crosses water** by ferry barge when riding around would be more than twice as far. The barge appears at the
  water's edge, he rows it across with his horse aboard to the nearest bank that takes him on by land, following the
  bends of a river and cutting through kelp and lily pads, and it steers clear of ocean monuments and drowned. It
  floats in water a single block deep.
- **Camps** at dusk. He finds a flat pitch, hammers in the pegs and lights a campfire with his flint and steel, and plays the harmonica until monsters come out,
  then sleeps in his tent with his boots out of the door. Mobs only notice him there from close by. The fire burns bright
  through the evening and dies down to embers by 3am. It's harmless, nobody can put it out, and it goes when he does.
  If he's attacked in camp, he runs for it until morning. In a village he skips the tent and sleeps in any bed nobody's
  in, a villager's included, and leaves it free in the morning.
- **Fights back for his life:** every hostile mob that hunts players hunts riders too. He carries two healing
  potions a day and drinks one when he's hurt.
- **Has mannerisms:** he waves at players he passes, tips his hat to villagers, wipes his brow in the desert, yawns
  in the evening, checks his pocket watch, reads the address as he gets close, and a dozen other little things.
- **Gets unstuck:** if he can't make progress, he teleports himself free. After five failed tries in a row he gives
  up and turns back.

The sender and the addressee get notices as the mail is collected and delivered.

## When it goes wrong

If a rider is killed, gets hopelessly lost, or finds the address gone, the mail is left as a **Lost Parcel** where it
happened. If he was killed, there's a skull in a cowboy hat beside it; if he got lost, a crumpled map. On the water
it's a soggy wreck floating at the surface.

The sender gets a letter saying what happened and where. A Lost Parcel can only be opened by the sender, the
addressee, or whoever killed the rider. Anyone else can read its label. It never despawns and explosions can't
destroy it.

## Advancements

Fifteen on their own Post Rider tab, from **Open for Business** (get a mailbox) through **Pen Pal**, **Neither Snow
nor Rain** and **Davy Jones' Locker** to the challenges **Meet the Crew** (be greeted by all 20 riders) and
**Rainbow Route** (a mailbox in every colour). One of them is a secret. Delivered mail notes on its tooltip if it
crossed water by barge or spent nights on the trail.

## Server settings

`config/postrider-server.toml`:

| Setting | Default | |
|---|---|---|
| `maxRiders` | 20 | Riders out at once (1-20). |
| `lookahead` | 48 | How far ahead riders plan block by block, in blocks (16-64). Each rider keeps enough chunks loaded to see this far (7x7 at 48, only the middle 3x3 ticking). The plan for the whole trip reads further, generating chunks where it has to. |
| `potionsPerDay` | 2 | Healing potions per rider per day (0-10). |
| `bargeSpeed` | 8.0 | Barge speed in blocks per second (2-16). |
| `firstCollection`, `lastCollection` | 6, 18 | The hours riders set out. Riders on the road still camp from dusk to 6am. |

## Commands

`/postrider`, for operators (permission level 2), mostly for trying things out:

| Command | What it does |
|---|---|
| `spawn [rider]` | Summons a rider in front of you (a random one, or one of the crew by id, e.g. `chris`). |
| `dispatch` | Sends out riders for any waiting mail now. |
| `status` | What the nearest rider is doing. |
| `whistle`, `dismount`, `vanish` | Calls, dismisses or removes the nearest rider's horse, or the rider. |
| `giveup` | Makes the nearest rider give up his delivery, as if he'd got lost. |
| `gesture <name>`, `activity <name>`, `satchel full\|empty` | Plays an animation or changes what he's doing or carrying. |

## Building

```
./gradlew build
```

Java 21. `./gradlew runClient` and `./gradlew runServer` start a development game. Every run loads two other mods
alongside, from Modrinth, as players are likely to have them and both change how chunks are loaded under the riders:
the Concurrent Chunk Management Engine (`c2me-neoforge`, alpha builds only for NeoForge 1.21.1), in the in-world tests
too, and Distant Horizons (`distanthorizons`), in every run but the in-world tests, where it crashes at startup (it
takes the game-test server for a dedicated one); the versions are in `build.gradle`.
The ice hopper is a boat for you: any boat and two packed ice make one, and it hops from the water up onto sea ice
and slides on without stopping (and off again), so a frozen ocean is no bar to it.

The models are in `blockbench/` and the rider skins are generated by `tools/rider_skins.js`, run inside Blockbench.
Animations are made on `postman.bbmodel` and copied to the slim build with `python tools/sync_slim_animations.py`
(along with any new hand prop and the base texture); a prop painted on the base skin afterwards is stamped onto all
twenty rider skins with `python tools/stamp_prop.py X Y WIDTH HEIGHT`.

## Testing

- **Unit tests** (`common/src/test/java`) run with `./gradlew build`.
- **In-world tests** (GameTests, in the `gametest` package) run with `./gradlew runGameTestServer`. It starts a
  headless server in a fresh world (`run-gametest/`), builds a small scene for each test, sets a rider going and
  watches him: delivering across open ground, rowing out to an island, walking down to nearby water, riding round a
  bay, crossing a stream by its bridge, leaping a ditch, rowing a narrow channel, and the barge floating in water a
  block deep and cutting through water plants. The task fails if any test does. The empty structure templates the
  tests are placed in are written by `tools/gen_test_structures.py`.
- **Results:** each delivery trial writes what the trip took (ticks on the road, barge launches, teleports, loops,
  stalls, and how much route planning it needed) to `run-gametest/trials.jsonl`. A run's world is deleted when the
  next run of its kind starts (watched, headless and game-test alike): they're hundreds of megabytes each, no run starts
  on an old one, and the last is kept until then so a trip that went wrong can be looked at. `python tools/trials.py` prints
  them against the saved baseline in `docs/trials/baseline.jsonl` and points out anything notably worse;
  `--save-baseline` keeps the latest run as the new baseline.
- **A real world, headless:** `./gradlew runHeadless -Pworld="New World (1)" -Pfrom=Testing -Pto=Smelly` copies that
  world from `run/saves/` to `run-headless/world`, starts a dedicated server on it, sets the clock to morning, posts a
  parcel from the one mailbox to the other, logs the rider's progress every fifteen seconds of game time (and his
  every move, with tracing on), and stops once it's delivered or lost, or after an hour of game time (a 3,000-block
  trip is a day's ride, and a night camped on the way is no failure; `-Ptimeout=<minutes>` for another cap). The server sprints (runs ticks as fast as it can) unless `-Psprint=false`; a fourteen-hundred-block
  trip takes a few minutes. `-Pto=random:3000` puts a box called "Far Post" down on dry land about that far from the
  first box, any way round, and posts to it (the log says where; `-Pto=at:<x>,<z>` tries that spot again).
  `-Pworld=new -Pfrom=spawn` has the server generate a fresh ordinary world instead and puts a "Spawn Post" down by
  its spawn to post from (`-Pseed=<seed>` for the same world again). In a live game `/postrider post "<from>" "<to>"` posts the same way.
- **Watching them:** `./gradlew runWatch` opens a flat creative world of its own (in `run-watch/`) and runs the
  lot in front of you; `./gradlew runWatch -Ptests=<name>` runs just one. The scenes are built around you and the
  riders set off; you're a spectator kept a few blocks off the rider most recently out, looking at him (the mouse
  swings the view round him), then the next once his is delivered, and so on (shift lets go until the next rider),
  and the game doesn't pause when you switch windows. The game stays open: `/test runall` or `/test run <name>`
  for another go, `/test clearall` to tidy up. (In any creative world started with `./gradlew runClient` the same
  `/test` commands work too; the name is the test method's, in lower case.)
- **Watching a real trip:** `./gradlew runWatch -Pworld=new -Pseed=<seed>` opens a fresh ordinary world (the same
  ground again for the same seed), puts a "Spawn Post" down by the spawn and a "Far Post" about 3,000 blocks off
  (`-Pto=random:<blocks>` or `-Pto=at:<x>,<z>` for elsewhere), posts, and follows the rider the same way, at the
  game's own pace, day and night; `-Pworld="New World (1)" -Pfrom=Testing -Pto=Smelly` does it across a copy of one
  of your worlds. In any world, `/postrider watch` starts the following (you're made a spectator; shift lets go until
  the next rider) and `/postrider watch stop` ends it, so a parcel sent with `/postrider post` can be watched too.
- **Screens:** `./gradlew runScreenshots` opens the mailbox screen with awkward names and messages, saves a picture
  of each to `run-screenshots/screenshots/postrider/`, and quits.

## Credits

See [CREDITS.md](CREDITS.md). Code and art are under the [MIT licence](LICENSE).

# Changelog

## 1.3.1

- A stuck rider is never teleported more than 16 blocks to get him going again (it was up to 24, and any distance
  straight up out of a cave), and any three tries to free him within two minutes now end the delivery (it was five
  in a row without getting on), so riders are jumped about less and do get stuck now and then (leaving a Lost Parcel,
  as before).

## 1.3.0

- New: Post Rider runs on Minecraft 26.3 (NeoForge 26.3, Java 25). Minecraft 1.21.1 carries on with the same features
  and version numbers, as a separate download. Worlds from 1.21.1 keep their mailboxes and deliveries, and servers
  their settings.
- Riders walk their horses past fire, campfires, magma, cactus and berry bushes, and keep to the way round them
  rather than cut the corner, so neither they nor their horses get singed in passing.
- On foot, riders step up stairs and slabs rather than hop them, so a narrow stair winding up a tower no longer
  throws them off the edge at a turn.
- Fixed: a rider could plan his trip from ground as it was up to ten minutes earlier, riding off for a way round a
  lake that had since been dug, or missing a bridge just built. The map he plans by now sees changes at once.
- Works alongside Dailies on 26.3 too (the mailbox and letters show in its recordings).

## 1.2.2

- New: with Dailies installed, the mailbox, letters being read and letters being written show in its recordings,
  drawn again in first-person playback and exports as the player saw them. Post Rider runs as before without Dailies.

## 1.2.1

- Fixed: a rider setting out on a long trip into land nobody had explored could freeze the server until it crashed,
  and crash it again each time it restarted. The unexplored ground is now worked out a little each tick, so the
  planning takes a minute or so longer and the server keeps running.
- Fixed: the chunks a rider held loaded along his route ahead stayed loaded after a restart, and after his mail was
  handed to another rider. They're now let go.

## 1.2.0

- New: the Ice Hopper, a boat for players with an upturned, iron-shod bow. Row it at sea ice and it hops up onto the
  ice and slides on without stopping, up a block of ice too, and drops back into the water off the edge. Craft it from
  any boat and two packed ice.
- Riders cross frozen oceans: the barge slides from open water onto sea ice and off again, climbs a block of ice, and
  treats higher ice as a bank. A rider camped on ice lights no fire and sits shivering instead.
- Long trips: routes over a few thousand blocks are planned in stages, ground nobody has explored yet is worked out
  from the world generator without generating it, and the chunks just ahead of the rider are kept loaded.
- Riders get about far better: they keep off cliff edges, never take two big drops in a row, leap only where there's
  headroom for the horse, climb ladders and vines to the top, open doors on foot, ride bubble columns, and take caves,
  mines and tight spiral stairs in their stride.
- Riders get unstuck far better: the way ahead is struck off the map and the route redone before anyone is
  teleported, a rider cut off on an island or an ice floe takes the barge, and the barge backs off a corner it's caught
  on and keeps to roomy water in tight leads.
- A rider whose spot in front of the sender's mailbox is blocked sets out from the nearest place he can stand.
- His hat sways as he walks and bobs as he rides, and now and then he eats a hunk of bread.
- Works alongside the Concurrent Chunk Management Engine and Distant Horizons.
- New operator commands: `/postrider watch` follows the riders as a spectator (`/postrider watch stop` to end it),
  and `/postrider post <from> <to>` posts a parcel between two named mailboxes.

## 1.1.2

- Fixed: a rider could row back and forth to the same stretch of shore, or turn back to the water chunks after
  landing. A shore that leads nowhere is now remembered and not landed on again, and a route worked out on the water
  is picked up where he is, not back at the water.
- Fixed: a rider could stand still with a good route ahead when his horse snagged on a corner, until he was
  teleported. He now works that stretch out again from where he is.
- More detail in the server log when a rider gets stuck.

## 1.1.1

- Fixed: a rider could be teleported over a river instead of rowing it, and circle round a bay. When there's no way
  on through the blocks ahead and open water to launch on, he takes the barge, and stuck at the water's edge he rows
  across rather than being teleported.
- Fixed: heading for the water, a rider could stop too far from the bank to launch the barge, and a channel too
  narrow for the long-range plan to see got him teleported over it. He now makes for the bank itself and launches
  from there.
- Fixed: on foot, riders ran as fast as their horses. They now run like a sprinting player.
- Fixed: landing on foot close to the mailbox, he no longer whistles for his horse just to send it away again.
- Riders' routing decisions (routes planned, barge launches and landings, ways that wouldn't plan, jumps free) are
  noted in the server log.

## 1.1.0

- Riders find their own way. After the pickup the rider steps away from the mailbox and studies a big paper map
  while he plans the whole trip, then plans the blocks ahead as he rides: round bays, ravines and cliffs, through
  caves and tunnels when they're shorter, leaping gaps, and on foot up ladders and through gaps too tight for the
  horse. He gets the map out again if his way is blocked.
- He never wades or swims. The barge is only for when riding round is more than twice as far, and then he rows as
  little as he can, straight across to the nearest bank that takes him on.
- The barge follows the bends of a river, slows for tight turns, cuts through kelp, seagrass and lily pads, floats
  in water a block deep, and lands at the bank instead of drifting short of it.
- On foot with the water close ahead, he walks down to it and whistles for his horse on the far side.
- Horses gallop at full speed the whole way, and only leap where the route crosses a gap (no more jumping back and
  forth over a hole).
- The mailbox screen is wider, and its messages, names and addresses always fit.

## 1.0.1

- Fixed: opening a parcel could lose its contents. Opening one now throws everything inside on the ground in front of
  you, the way emptying a bundle does.

## 1.0.0

First release, for Minecraft 1.21.1 with NeoForge 21.1.

- Mailboxes in 16 colours: a 27-delivery inbox, a flag, addresses and a send screen.
- Letters you write and sign, with postmarks, and parcels of up to 9 stacks.
- Twenty riders, each with their own face, outfit, name and horse, in a regular and a slim build.
- Real journeys: planned routes, gap-jumping horses, ferry barges across water, and getting unstuck.
- Camping at dusk: the tent, the campfire, the harmonica, and sleeping with his boots out of the door.
- Danger on the road: hostile mobs hunt riders, who carry healing potions and run from trouble in camp.
- Mannerisms: waving, tipping his hat, wiping his brow and a dozen more.
- Lost Parcels and failure letters when a rider doesn't make it.
- Fifteen advancements.
- Server settings for rider count, route planning range, potions, barge speed and collection hours.
- Operator commands for trying things out.

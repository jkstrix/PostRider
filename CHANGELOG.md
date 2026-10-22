# Changelog

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
